package com.bagusxmahendra.mltf.supervisor_agent.service;

import com.bagusxmahendra.mltf.supervisor_agent.config.SupervisorAgentProperties;
import com.bagusxmahendra.mltf.supervisor_agent.dto.ApplicationDocumentResponse;
import com.bagusxmahendra.mltf.supervisor_agent.prompt.LoanApplicationPromptProvider;
import com.bagusxmahendra.mltf.supervisor_agent.tools.LoanApplicationTools;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.SessionKey;
import com.google.adk.tools.FunctionTool;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;

/**
 * Service orchestrating the LoanApplicationAgent using Google ADK.
 * Handles loan application document validation, extraction of applicant/application/property fields,
 * and saving metadata to Spanner tables.
 */
@Service
public class LoanApplicationAgentService {

    private static final Logger log = LoggerFactory.getLogger(LoanApplicationAgentService.class);

    private final SupervisorAgentProperties properties;
    private final LoanApplicationPromptProvider promptProvider;
    private final LoanApplicationTools loanApplicationTools;
    private final ObjectMapper objectMapper;

    private Client genAiClient;
    private LlmAgent adkAgent;
    private InMemoryRunner adkRunner;

    public LoanApplicationAgentService(
            SupervisorAgentProperties properties,
            LoanApplicationPromptProvider promptProvider,
            LoanApplicationTools loanApplicationTools
    ) {
        this.properties = properties;
        this.promptProvider = promptProvider;
        this.loanApplicationTools = loanApplicationTools;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @PostConstruct
    public void init() {
        try {
            initAdkAgent();
            log.info("Google ADK LoanApplicationAgent initialized successfully with model: {}", properties.getModel());
        } catch (Exception e) {
            log.warn("Google ADK LoanApplicationAgent deferred initialization: {}", e.getMessage());
        }
    }

    private synchronized void initAdkAgent() {
        if (this.adkAgent != null && this.adkRunner != null) {
            return;
        }

        Client.Builder clientBuilder = Client.builder();
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            clientBuilder.apiKey(properties.getApiKey());
        }
        if (properties.isUseVertexAi()) {
            clientBuilder.vertexAI(true);
            if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
                clientBuilder.project(properties.getProjectId());
            }
            if (properties.getLocation() != null && !properties.getLocation().isBlank()) {
                clientBuilder.location(properties.getLocation());
            }
        }

        this.genAiClient = clientBuilder.build();

        Gemini gemini = Gemini.builder()
                .modelName(properties.getModel())
                .apiClient(this.genAiClient)
                .build();

        GenerateContentConfig contentConfig = GenerateContentConfig.builder()
                .temperature(properties.getTemperature())
                .build();

        List<Object> tools = new ArrayList<>();
        if (this.loanApplicationTools != null) {
            try {
                tools.add(FunctionTool.create(loanApplicationTools, "validateDocument"));
                tools.add(FunctionTool.create(loanApplicationTools, "checkDataSimilarity"));
                tools.add(FunctionTool.create(loanApplicationTools, "saveApplication"));
                tools.add(FunctionTool.create(loanApplicationTools, "saveApplicant"));
                tools.add(FunctionTool.create(loanApplicationTools, "saveProperty"));
                tools.add(FunctionTool.create(loanApplicationTools, "saveDocument"));
                log.info("Registered 6 ADK tools with LoanApplicationAgent: validateDocument, checkDataSimilarity, saveApplication, saveApplicant, saveProperty, saveDocument");
            } catch (Exception e) {
                log.warn("Could not register ADK LoanApplication tools: {}", e.getMessage());
            }
        }

        LlmAgent.Builder agentBuilder = LlmAgent.builder()
                .name("LoanApplicationAgent")
                .description("Agent orchestrating loan application document validation, field extraction, application updating, and document persisting")
                .instruction(promptProvider.getSystemPrompt())
                .model(gemini)
                .generateContentConfig(contentConfig);

        if (!tools.isEmpty()) {
            agentBuilder.tools(tools);
        }

        this.adkAgent = agentBuilder.build();
        this.adkRunner = new InMemoryRunner(this.adkAgent, "loan-application-app");
    }

    public Mono<ApplicationDocumentResponse> processDocument(
            String applicationId,
            String userId,
            String documentId,
            String filename,
            String gcsUrl,
            String contentType
    ) {
        log.info("LoanApplicationAgent starting orchestration for appId: {}, docId: {}, gcsUrl: {}",
                applicationId, documentId, gcsUrl);

        return Mono.defer(() -> {
            try {
                initAdkAgent();
                String promptText = promptProvider.buildUserPrompt(applicationId, userId, documentId, filename, gcsUrl, contentType);
                Content content = Content.builder()
                        .role("user")
                        .parts(List.of(Part.fromText(promptText)))
                        .build();

                String sessionUserId = "loan-user-" + UUID.randomUUID().toString().substring(0, 8);
                String sessionId = "loan-sess-" + UUID.randomUUID().toString();
                SessionKey sessionKey = new SessionKey(adkRunner.appName(), sessionUserId, sessionId);

                return Mono.<com.google.adk.sessions.Session>create(sink -> {
                    adkRunner.sessionService().createSession(sessionKey)
                            .subscribe(sink::success, sink::error);
                })
                .flatMap(session -> {
                    log.info("Created ADK LoanApplication session: {} for user: {}", session.id(), sessionUserId);
                    return Flux.from(adkRunner.runAsync(sessionKey, content))
                            .collectList()
                            .map(this::extractTextFromEvents)
                            .map(rawJson -> parseAgentResponse(rawJson, filename, documentId, applicationId, userId, gcsUrl, contentType));
                });
            } catch (Exception e) {
                log.warn("ADK Agent direct execution deferred ({}), performing programmatic synthesis", e.getMessage());
                return processProgrammatically(applicationId, userId, documentId, filename, gcsUrl, contentType);
            }
        })
        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()), Mono.defer(() -> {
            log.warn("ADK Agent timed out after {}s, falling back to programmatic processing", properties.getTimeoutSeconds());
            return processProgrammatically(applicationId, userId, documentId, filename, gcsUrl, contentType);
        }))
        .onErrorResume(err -> {
            log.warn("Error during ADK LLM orchestration ({}), falling back to programmatic processing", err.getMessage());
            return processProgrammatically(applicationId, userId, documentId, filename, gcsUrl, contentType);
        })
        .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApplicationDocumentResponse> processProgrammatically(
            String applicationId,
            String userId,
            String documentId,
            String filename,
            String gcsUrl,
            String contentType
    ) {
        return Mono.fromCallable(() -> {
            log.info("Executing LoanApplication programmatic processing pipeline for applicationId: {}", applicationId);

            // Step 1: Validate document
            Map<String, Object> docResult = loanApplicationTools.validateDocument(gcsUrl, contentType, null);

            String status = extractString(docResult, "status", "SUCCESS");
            String message = extractString(docResult, "message", "Document validated successfully");
            boolean isTampered = isDocumentTampered(docResult);

            if (isTampered) {
                status = "FAILED";
                message = "Document validation failed: Tampering detected";
            }

            // Step 2: If document is valid, extract fields and save to tables
            if ("SUCCESS".equalsIgnoreCase(status)) {
                Map<String, Object> extractedFields = extractMap(docResult, "extractedFields");
                Map<String, Object> applicantData = new LinkedHashMap<>();
                Map<String, Object> applicationData = new LinkedHashMap<>();
                Map<String, Object> propertyData = new LinkedHashMap<>();

                if (extractedFields != null && !extractedFields.isEmpty()) {
                    routeExtractedFields(extractedFields, applicantData, applicationData, propertyData);
                }

                // Check similarity against existing database records BEFORE saving
                Map<String, Object> simResult = loanApplicationTools.checkDataSimilarity(
                        applicationId,
                        applicantData,
                        applicationData,
                        propertyData
                );

                boolean hasConflict = Boolean.TRUE.equals(simResult.get("hasConflict"))
                        || "CONFLICT_DETECTED".equalsIgnoreCase(String.valueOf(simResult.get("status")));

                if (hasConflict) {
                    status = "FAILED";
                    message = extractString(simResult, "message", "Conflicting data detected in document compared to existing records");
                    log.warn("Document validation blocked by similarity check: {}", message);
                } else {
                    // Save data to respective tables via tools
                    if (!applicationData.isEmpty()) {
                        Map<String, Object> saveRes = loanApplicationTools.saveApplication(applicationId, userId, applicationData);
                        if ("FAILED".equalsIgnoreCase(extractString(saveRes, "status", ""))) {
                            status = "FAILED";
                            message = extractString(saveRes, "error", "Conflicting application data in document");
                        }
                    }
                    if ("SUCCESS".equalsIgnoreCase(status) && !applicantData.isEmpty()) {
                        Map<String, Object> saveRes = loanApplicationTools.saveApplicant(applicationId, userId, applicantData);
                        if ("FAILED".equalsIgnoreCase(extractString(saveRes, "status", ""))) {
                            status = "FAILED";
                            message = extractString(saveRes, "error", "Conflicting applicant data in document");
                        }
                    }
                    if ("SUCCESS".equalsIgnoreCase(status) && !propertyData.isEmpty()) {
                        Map<String, Object> saveRes = loanApplicationTools.saveProperty(applicationId, null, propertyData);
                        if ("FAILED".equalsIgnoreCase(extractString(saveRes, "status", ""))) {
                            status = "FAILED";
                            message = extractString(saveRes, "error", "Conflicting property data in document");
                        }
                    }
                }
            }

            // Step 3: Save document record in table 'document'
            String processingDetails;
            try {
                processingDetails = objectMapper.writeValueAsString(docResult);
            } catch (Exception e) {
                processingDetails = "{}";
            }

            loanApplicationTools.saveDocument(
                    applicationId,
                    documentId,
                    filename,
                    gcsUrl,
                    contentType,
                    status,
                    message,
                    processingDetails
            );

            return new ApplicationDocumentResponse(
                    filename,
                    documentId,
                    status,
                    message
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void routeExtractedFields(
            Map<String, Object> extractedFields,
            Map<String, Object> applicantData,
            Map<String, Object> applicationData,
            Map<String, Object> propertyData
    ) {
        // Known applicant fields
        Set<String> applicantKeys = Set.of(
                "race", "nationality", "fullname", "full_name", "idno", "id_no", "idnumber", "id_number",
                "idtype", "id_type", "gender", "sex", "maritalstatus", "marital_status", "dateofbirth",
                "date_of_birth", "dob", "dependentscount", "dependents_count", "educationlevel", "education_level",
                "mobilephone", "mobile_phone", "phonenumber", "residentialphone", "residential_phone", "email",
                "permaddress", "perm_address", "address", "permpostcode", "perm_postcode", "postalcode", "postcode",
                "permcity", "perm_city", "city", "permstate", "perm_state", "state", "mailaddress", "mail_address",
                "mailpostcode", "mail_postcode", "employmentstatus", "employment_status", "employername",
                "employer_name", "natureofbusiness", "nature_of_business", "occupation", "jobposition", "job_position",
                "lengthofserviceyears", "length_of_service_years", "monthlygrossrm", "monthly_gross_rm",
                "monthlyincome", "annualgrossrm", "annual_gross_rm", "emergencyname", "emergency_name",
                "emergencyrelationship", "emergency_relationship", "emergencyphone", "emergency_phone",
                "spousefullname", "spouse_full_name", "spouseidno", "spouse_id_no", "spousemobile",
                "spouse_mobile", "spouseemployer", "spouse_employer", "spousemonthlygrossrm", "spouse_monthly_gross_rm", "othercommitments", "other_commitments", "closerelatives", "close_relatives"
        );

        // Known application fields
        Set<String> appKeys = Set.of(
                "facilitytype", "facility_type", "facilitypurpose", "facility_purpose",
                "bankselection", "bank_selection", "marketingconsent", "marketing_consent",
                "applicationtype", "application_type"
        );

        // Known property fields
        Set<String> propertyKeys = Set.of(
                "propertytype", "property_type", "propertystatus", "property_status",
                "developername", "developer_name", "projectname", "project_name",
                "contractorname", "contractor_name", "spapricerm", "spa_price_rm", "spaprice",
                "openmarketrm", "open_market_rm", "renovationvaluerm", "renovation_value_rm",
                "propertyaddress", "property_address", "propertypostcode", "property_postcode",
                "propertycity", "property_city", "propertystate", "property_state",
                "titlenumber", "title_number", "titletype", "title_type", "lotnumber", "lot_number",
                "mukim", "district", "isowneroccupied", "is_owner_occupied", "isfirsttimebuyer", "is_first_time_buyer"
        );

        for (Map.Entry<String, Object> entry : extractedFields.entrySet()) {
            String rawKey = entry.getKey();
            if (rawKey == null) continue;
            String normKey = rawKey.toLowerCase().replace("_", "").replace("-", "");

            if (entry.getValue() instanceof Map<?, ?> nestedMap) {
                // If nested structure like "applicant": { "race": "Malay" }
                if (normKey.contains("applicant")) {
                    for (Map.Entry<?, ?> ne : nestedMap.entrySet()) {
                        if (ne.getKey() != null && ne.getValue() != null) {
                            applicantData.put(ne.getKey().toString(), ne.getValue());
                        }
                    }
                } else if (normKey.contains("property")) {
                    for (Map.Entry<?, ?> ne : nestedMap.entrySet()) {
                        if (ne.getKey() != null && ne.getValue() != null) {
                            propertyData.put(ne.getKey().toString(), ne.getValue());
                        }
                    }
                } else if (normKey.contains("application")) {
                    for (Map.Entry<?, ?> ne : nestedMap.entrySet()) {
                        if (ne.getKey() != null && ne.getValue() != null) {
                            applicationData.put(ne.getKey().toString(), ne.getValue());
                        }
                    }
                }
            } else {
                if (applicantKeys.contains(rawKey.toLowerCase()) || applicantKeys.contains(normKey)) {
                    applicantData.put(rawKey, entry.getValue());
                } else if (propertyKeys.contains(rawKey.toLowerCase()) || propertyKeys.contains(normKey)) {
                    propertyData.put(rawKey, entry.getValue());
                } else if (appKeys.contains(rawKey.toLowerCase()) || appKeys.contains(normKey)) {
                    applicationData.put(rawKey, entry.getValue());
                } else {
                    // Default to applicant data if generic
                    applicantData.put(rawKey, entry.getValue());
                }
            }
        }
    }

    private String extractTextFromEvents(List<Event> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (event.finalResponse() && event.content().isPresent()) {
                String text = extractTextFromContent(event.content().get());
                if (!text.isBlank()) return text.trim();
            }
        }

        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (event.content().isPresent()) {
                String text = extractTextFromContent(event.content().get());
                if (!text.isBlank()) return text.trim();
            }
            if (event.stringifyContent() != null && !event.stringifyContent().isBlank()) {
                return event.stringifyContent().trim();
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Event event : events) {
            if (event.content().isPresent()) {
                sb.append(extractTextFromContent(event.content().get()));
            }
        }
        return sb.toString().trim();
    }

    private String extractTextFromContent(Content content) {
        StringBuilder sb = new StringBuilder();
        if (content.parts().isPresent()) {
            for (Part part : content.parts().get()) {
                part.text().ifPresent(sb::append);
            }
        }
        return sb.toString();
    }

    private ApplicationDocumentResponse parseAgentResponse(
            String rawJson,
            String filename,
            String documentId,
            String applicationId,
            String userId,
            String gcsUrl,
            String contentType
    ) {
        String clean = sanitizeJson(rawJson);
        try {
            JsonNode node = objectMapper.readTree(clean);
            String docFilename = node.has("documentFilename") ? node.get("documentFilename").asText() : filename;
            String docId = node.has("documentId") ? node.get("documentId").asText() : documentId;
            String docStatus = node.has("documentStatus") ? node.get("documentStatus").asText() : "SUCCESS";
            String docMessage = node.has("documentMessage") ? node.get("documentMessage").asText() : "Document processed successfully";

            return new ApplicationDocumentResponse(
                    docFilename,
                    docId,
                    docStatus,
                    docMessage
            );
        } catch (Exception e) {
            log.warn("Failed to parse ADK LoanApplicationAgent JSON ({}), falling back to programmatic", e.getMessage());
            return processProgrammatically(applicationId, userId, documentId, filename, gcsUrl, contentType).block();
        }
    }

    private String sanitizeJson(String text) {
        if (text == null) return "{}";
        String s = text.trim();
        if (s.startsWith("```json")) s = s.substring(7);
        else if (s.startsWith("```")) s = s.substring(3);
        if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        return s.trim();
    }

    private String extractString(Map<String, Object> map, String key, String fallback) {
        if (map == null) return fallback;
        if (map.containsKey(key) && map.get(key) != null) {
            return map.get(key).toString();
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        if (map == null) return Collections.emptyMap();
        if (map.containsKey(key) && map.get(key) instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Collections.emptyMap();
    }

    private boolean isDocumentTampered(Map<String, Object> docResult) {
        if (docResult == null) return false;
        if (docResult.containsKey("pixelLevelCheck") && docResult.get("pixelLevelCheck") instanceof Map<?, ?> px) {
            if (px.containsKey("isTampered")) {
                Object val = px.get("isTampered");
                if (val instanceof Boolean b) return b;
                return Boolean.parseBoolean(String.valueOf(val));
            }
        }
        return false;
    }
}

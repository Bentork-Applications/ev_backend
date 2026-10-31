package com.bentork.ev_system.service.followup;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bentork.ev_system.dto.followup.FollowUpCampaignResponse;
import com.bentork.ev_system.dto.followup.FollowUpRoundResponse;
import com.bentork.ev_system.enums.LeadSource;
import com.bentork.ev_system.enums.LeadStatus;
import com.bentork.ev_system.model.Admin;
import com.bentork.ev_system.model.Lead;
import com.bentork.ev_system.model.followup.LeadFollowUpCampaign;
import com.bentork.ev_system.model.followup.LeadFollowUpRound;
import com.bentork.ev_system.model.followup.LeadImportBatch;
import com.bentork.ev_system.model.followup.LeadImportBatchLead;
import com.bentork.ev_system.repository.AdminRepository;
import com.bentork.ev_system.repository.LeadRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpCampaignRepository;
import com.bentork.ev_system.repository.followup.LeadFollowUpRoundRepository;
import com.bentork.ev_system.repository.followup.LeadImportBatchLeadRepository;
import com.bentork.ev_system.repository.followup.LeadImportBatchRepository;
import com.bentork.ev_system.service.DuplicateMatchingService;
import com.bentork.ev_system.util.PhoneNormalizationUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles the one-time Excel/CSV import for the 5-round follow-up campaign.
 * Integrates with canonical CRM leads and automatically scaffolds all 5 sequential rounds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowUpImportService {

    private final LeadImportBatchRepository batchRepository;
    private final LeadImportBatchLeadRepository batchLeadRepository;
    private final LeadFollowUpCampaignRepository campaignRepository;
    private final LeadFollowUpRoundRepository roundRepository;
    private final LeadRepository leadRepository;
    private final AdminRepository adminRepository;
    private final DuplicateMatchingService duplicateMatchingService;

    private static final AtomicInteger BATCH_SEQ = new AtomicInteger(100);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParsedRow {
        private int rowNumber;
        private String name;
        private String phone;
        private String email;
        private String company;
        private String city;
        private String state;
        private String notes;
        private Double estimatedValue;
    }

    /**
     * Executes the one-time import of an Excel/CSV file, creates the LeadImportBatch,
     * links canonical CRM Lead records, creates the LeadFollowUpCampaign, and scaffolds Rounds 1 to 5.
     */
    @Transactional
    public FollowUpCampaignResponse importAndCreateCampaign(
            MultipartFile file,
            String campaignName,
            String channel,
            Long defaultOwnerAdminId,
            String adminEmail) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is empty or missing");
        }

        String originalFileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.xlsx";
        String normalizedChannel = (channel != null && !channel.trim().isEmpty()) ? channel.trim().toUpperCase() : "WHATSAPP";

        if (!normalizedChannel.equals("WHATSAPP") && !normalizedChannel.equals("EMAIL") && !normalizedChannel.equals("BOTH")) {
            throw new IllegalArgumentException("Invalid channel: " + channel + ". Supported: WHATSAPP, EMAIL, BOTH");
        }

        // 1. Calculate file checksum
        String checksum;
        try {
            checksum = calculateSha256(file.getInputStream());
        } catch (Exception e) {
            checksum = "unknown_" + System.currentTimeMillis();
        }

        // 2. Parse file rows (Excel or CSV)
        List<ParsedRow> rows;
        try {
            String lowerName = originalFileName.toLowerCase();
            if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                rows = parseExcel(file.getInputStream());
            } else if (lowerName.endsWith(".csv") || lowerName.endsWith(".txt")) {
                rows = parseCsv(file.getInputStream());
            } else {
                throw new IllegalArgumentException("Unsupported file type. Please upload an Excel (.xlsx, .xls) or CSV file.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse file: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Error parsing import file: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No data rows found in the uploaded file");
        }

        // 3. Resolve Admin owner
        Admin ownerAdmin = null;
        if (defaultOwnerAdminId != null) {
            ownerAdmin = adminRepository.findById(defaultOwnerAdminId).orElse(null);
        }
        if (ownerAdmin == null && adminEmail != null) {
            ownerAdmin = adminRepository.findByEmail(adminEmail).orElse(null);
        }

        // 4. Create LeadImportBatch entity
        String batchNumber = generateBatchNumber();
        LeadImportBatch batch = LeadImportBatch.builder()
                .batchNumber(batchNumber)
                .originalFileName(originalFileName)
                .fileChecksum(checksum)
                .fileSizeBytes(file.getSize())
                .totalRows(rows.size())
                .validRows(0)
                .invalidRows(0)
                .newLeadsCreated(0)
                .existingLeadsMatched(0)
                .uploadedByAdminEmail(adminEmail)
                .build();
        batch = batchRepository.save(batch);

        // 5. Process rows and map to canonical Lead records
        int validRows = 0;
        int invalidRows = 0;
        int newLeads = 0;
        int matchedLeads = 0;

        Set<Long> processedLeadIdsInBatch = new HashSet<>();

        for (ParsedRow row : rows) {
            if ((row.getName() == null || row.getName().trim().isEmpty()) &&
                (row.getPhone() == null || row.getPhone().trim().isEmpty())) {
                invalidRows++;
                continue;
            }

            String normalizedPhone = null;
            if (row.getPhone() != null && !row.getPhone().trim().isEmpty()) {
                normalizedPhone = PhoneNormalizationUtil.normalize(row.getPhone());
            }

            Lead targetLead = null;

            // Check duplicate matching against existing canonical CRM leads
            if (normalizedPhone != null && !normalizedPhone.isEmpty()) {
                DuplicateMatchingService.MatchResult match = duplicateMatchingService.matchByPhone(normalizedPhone);
                if (match.isMatched() && match.getMatchedLead() != null) {
                    targetLead = match.getMatchedLead();
                    matchedLeads++;
                }
            }

            // If no match found, create a new canonical CRM Lead
            if (targetLead == null) {
                targetLead = new Lead();
                targetLead.setLeadNumber(generateLeadNumber());
                String contactDisplayName = row.getName() != null && !row.getName().trim().isEmpty() ? row.getName().trim() : "Follow-up Lead";
                targetLead.setTitle(contactDisplayName + (row.getCompany() != null ? " - " + row.getCompany() : ""));
                targetLead.setSource(LeadSource.WHATSAPP_CAMPAIGN.getValue());
                targetLead.setStatus(LeadStatus.NEW.getValue());
                targetLead.setPhone(normalizedPhone);
                targetLead.setEmail(row.getEmail());
                targetLead.setCity(row.getCity());
                targetLead.setState(row.getState());
                targetLead.setEstimatedValue(row.getEstimatedValue());
                targetLead.setNotes(row.getNotes());
                targetLead.setOwnerAdmin(ownerAdmin);
                targetLead.setCampaignSource(campaignName);
                targetLead = leadRepository.save(targetLead);
                newLeads++;
            }

            // Ensure uniqueness within the batch
            if (!processedLeadIdsInBatch.contains(targetLead.getId())) {
                processedLeadIdsInBatch.add(targetLead.getId());

                LeadImportBatchLead batchLead = LeadImportBatchLead.builder()
                        .batch(batch)
                        .lead(targetLead)
                        .rowNumber(row.getRowNumber())
                        .rawName(row.getName())
                        .rawPhone(row.getPhone())
                        .rawEmail(row.getEmail())
                        .rawCompany(row.getCompany())
                        .build();
                batchLeadRepository.save(batchLead);
                validRows++;
            }
        }

        // Update batch counts
        batch.setValidRows(validRows);
        batch.setInvalidRows(invalidRows);
        batch.setNewLeadsCreated(newLeads);
        batch.setExistingLeadsMatched(matchedLeads);
        batchRepository.save(batch);

        // 6. Create LeadFollowUpCampaign entity
        String resolvedCampaignName = (campaignName != null && !campaignName.trim().isEmpty())
                ? campaignName.trim()
                : "Follow-up Campaign - " + batchNumber;

        LeadFollowUpCampaign campaign = LeadFollowUpCampaign.builder()
                .name(resolvedCampaignName)
                .batch(batch)
                .channel(normalizedChannel)
                .status("DRAFT")
                .currentRoundNumber(1)
                .totalRounds(5)
                .completedRounds(0)
                .ownerAdmin(ownerAdmin)
                .createdByAdminEmail(adminEmail)
                .build();
        campaign = campaignRepository.save(campaign);

        // 7. Scaffold all 5 cascading rounds
        List<LeadFollowUpRound> rounds = new ArrayList<>();
        LeadFollowUpRound parentRound = null;

        for (int r = 1; r <= 5; r++) {
            LeadFollowUpRound round = LeadFollowUpRound.builder()
                    .campaign(campaign)
                    .roundNumber(r)
                    .parentRound(parentRound)
                    .channel(normalizedChannel)
                    .subject(resolvedCampaignName + " - Follow-up Round " + r)
                    .messageBody("Hello {{name}},\n\nThis is round " + r + " follow-up regarding our previous communication.\n\nBest regards,\nBentork Sales Team")
                    .status("PENDING")
                    .selectedLeadCount(0)
                    .sentCount(0)
                    .deliveredCount(0)
                    .failedCount(0)
                    .build();

            round = roundRepository.save(round);
            rounds.add(round);
            parentRound = round; // Chain for next round
        }

        log.info("Initialized 5-round follow-up campaign '{}' (ID: {}) with batch '{}' ({} valid leads)",
                campaign.getName(), campaign.getId(), batch.getBatchNumber(), validRows);

        return mapCampaignToResponse(campaign, batch, rounds);
    }

    public FollowUpCampaignResponse mapCampaignToResponse(
            LeadFollowUpCampaign campaign, LeadImportBatch batch, List<LeadFollowUpRound> rounds) {

        List<FollowUpRoundResponse> roundResponses = new ArrayList<>();
        if (rounds != null) {
            for (LeadFollowUpRound r : rounds) {
                roundResponses.add(FollowUpRoundResponse.builder()
                        .id(r.getId())
                        .campaignId(campaign.getId())
                        .roundNumber(r.getRoundNumber())
                        .parentRoundId(r.getParentRound() != null ? r.getParentRound().getId() : null)
                        .channel(r.getChannel())
                        .subject(r.getSubject())
                        .messageBody(r.getMessageBody())
                        .status(r.getStatus())
                        .selectedLeadCount(r.getSelectedLeadCount())
                        .sentCount(r.getSentCount())
                        .deliveredCount(r.getDeliveredCount())
                        .failedCount(r.getFailedCount())
                        .startedAt(r.getStartedAt())
                        .completedAt(r.getCompletedAt())
                        .lastTriggeredByEmail(r.getLastTriggeredByEmail())
                        .attachments(new ArrayList<>())
                        .build());
            }
        }

        return FollowUpCampaignResponse.builder()
                .id(campaign.getId())
                .name(campaign.getName())
                .batchId(batch.getId())
                .batchNumber(batch.getBatchNumber())
                .originalFileName(batch.getOriginalFileName())
                .totalBatchLeads(batch.getValidRows())
                .channel(campaign.getChannel())
                .status(campaign.getStatus())
                .currentRoundNumber(campaign.getCurrentRoundNumber())
                .totalRounds(campaign.getTotalRounds())
                .completedRounds(campaign.getCompletedRounds())
                .ownerAdminId(campaign.getOwnerAdmin() != null ? campaign.getOwnerAdmin().getId() : null)
                .ownerAdminName(campaign.getOwnerAdmin() != null ? campaign.getOwnerAdmin().getName() : null)
                .createdByAdminEmail(campaign.getCreatedByAdminEmail())
                .completedAt(campaign.getCompletedAt())
                .createdAt(campaign.getCreatedAt())
                .updatedAt(campaign.getUpdatedAt())
                .rounds(roundResponses)
                .build();
    }

    private List<ParsedRow> parseExcel(InputStream is) throws Exception {
        List<ParsedRow> result = new ArrayList<>();
        Workbook workbook = WorkbookFactory.create(is);
        Sheet sheet = workbook.getSheetAt(0);

        if (sheet == null || sheet.getLastRowNum() < 1) {
            workbook.close();
            return result;
        }

        Row headerRow = sheet.getRow(0);
        Map<String, Integer> colMap = buildColumnMap(headerRow);

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String name = getCellValue(row, colMap.get("name"));
            String phone = getCellValue(row, colMap.get("phone"));
            String email = getCellValue(row, colMap.get("email"));
            String company = getCellValue(row, colMap.get("company"));
            String city = getCellValue(row, colMap.get("city"));
            String state = getCellValue(row, colMap.get("state"));
            String notes = getCellValue(row, colMap.get("notes"));
            String estimatedValStr = getCellValue(row, colMap.get("estimatedvalue"));

            Double estimatedValue = null;
            if (estimatedValStr != null && !estimatedValStr.trim().isEmpty()) {
                try {
                    estimatedValue = Double.parseDouble(estimatedValStr.trim());
                } catch (Exception ignored) {}
            }

            result.add(ParsedRow.builder()
                    .rowNumber(r)
                    .name(name)
                    .phone(phone)
                    .email(email)
                    .company(company)
                    .city(city)
                    .state(state)
                    .notes(notes)
                    .estimatedValue(estimatedValue)
                    .build());
        }

        workbook.close();
        return result;
    }

    private List<ParsedRow> parseCsv(InputStream is) throws Exception {
        List<ParsedRow> result = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));

        String headerLine = reader.readLine();
        if (headerLine == null) return result;

        String[] headers = parseCsvLine(headerLine);
        Map<String, Integer> colMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String norm = headers[i].trim().toLowerCase().replaceAll("[^a-z]", "");
            colMap.put(norm, i);
        }

        String line;
        int rowNum = 1;
        while ((line = reader.readLine()) != null) {
            rowNum++;
            if (line.trim().isEmpty()) continue;
            String[] vals = parseCsvLine(line);

            String name = getCsvValue(vals, colMap, "name", "contactname", "customername");
            String phone = getCsvValue(vals, colMap, "phone", "contactphone", "mobile", "mobilenumber");
            String email = getCsvValue(vals, colMap, "email", "contactemail");
            String company = getCsvValue(vals, colMap, "company", "companyname");
            String city = getCsvValue(vals, colMap, "city");
            String state = getCsvValue(vals, colMap, "state");
            String notes = getCsvValue(vals, colMap, "notes", "remark", "remarks");

            result.add(ParsedRow.builder()
                    .rowNumber(rowNum)
                    .name(name)
                    .phone(phone)
                    .email(email)
                    .company(company)
                    .city(city)
                    .state(state)
                    .notes(notes)
                    .build());
        }

        return result;
    }

    private String getCsvValue(String[] vals, Map<String, Integer> colMap, String... keys) {
        for (String k : keys) {
            Integer idx = colMap.get(k);
            if (idx != null && idx < vals.length && !vals[idx].trim().isEmpty()) {
                return vals[idx].trim();
            }
        }
        return null;
    }

    private String[] parseCsvLine(String line) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                list.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        return list.toArray(new String[0]);
    }

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell c = headerRow.getCell(i);
            if (c != null) {
                String val = c.getStringCellValue().trim().toLowerCase().replaceAll("[^a-z]", "");
                if (val.contains("name")) map.put("name", i);
                else if (val.contains("phone") || val.contains("mobile")) map.put("phone", i);
                else if (val.contains("email")) map.put("email", i);
                else if (val.contains("company")) map.put("company", i);
                else if (val.contains("city")) map.put("city", i);
                else if (val.contains("state")) map.put("state", i);
                else if (val.contains("note") || val.contains("remark")) map.put("notes", i);
                else if (val.contains("value") || val.contains("amount")) map.put("estimatedvalue", i);
            }
        }
        return map;
    }

    private String getCellValue(Row row, Integer colIdx) {
        if (colIdx == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            long val = (long) cell.getNumericCellValue();
            return String.valueOf(val);
        }
        return null;
    }

    private String generateBatchNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "BATCH-" + datePart + "-" + BATCH_SEQ.incrementAndGet();
    }

    private String generateLeadNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = (int) (Math.random() * 9000) + 1000;
        return "LD-" + datePart + "-" + randomPart;
    }

    private String calculateSha256(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}


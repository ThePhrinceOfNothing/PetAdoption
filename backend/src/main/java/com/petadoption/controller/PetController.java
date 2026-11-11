package com.petadoption.controller;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PetController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Defensive Input Validation Helpers
    private static String validateAndClampString(Object val, String fieldName, int minLen, int maxLen, boolean required, String defaultVal) {
        if (val == null) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            return defaultVal;
        }
        String s = val.toString().trim();
        if (s.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(fieldName + " cannot be blank.");
            }
            return defaultVal;
        }
        if (s.length() < minLen) {
            throw new IllegalArgumentException(fieldName + " must be at least " + minLen + " characters.");
        }
        if (s.length() > maxLen) {
            return s.substring(0, maxLen);
        }
        return s;
    }

    private static int validateAndClampAge(Object val) {
        if (val == null) return 1;
        String raw = val.toString().trim().replaceAll("[^0-9]", "");
        if (raw.isEmpty()) return 1;
        try {
            int age = Integer.parseInt(raw);
            if (age < 0) return 0;
            if (age > 35) return 35;
            return age;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static java.sql.Date validateAndParseDate(Object val, String fieldName) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try {
            java.sql.Date d = java.sql.Date.valueOf(s);
            long millis = d.getTime();
            long year1990 = java.sql.Date.valueOf("1990-01-01").getTime();
            long year2100 = java.sql.Date.valueOf("2100-01-01").getTime();
            if (millis < year1990 || millis > year2100) {
                throw new IllegalArgumentException(fieldName + " date is outside the valid range (1990 - 2100).");
            }
            return d;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("outside the valid range")) throw e;
            throw new IllegalArgumentException(fieldName + " must be in valid YYYY-MM-DD date format.");
        }
    }

    @PostConstruct
    public void initPetImagesTable() {
        new Thread(() -> {
            boolean ready = false;
            for (int attempt = 1; attempt <= 10 && !ready; attempt++) {
                try {
                    Thread.sleep(1500);
                    String createTableSql = """
                        CREATE TABLE IF NOT EXISTS Pet_Images (
                            ImageID SERIAL PRIMARY KEY,
                            PetID INT REFERENCES Pets(PetID) ON DELETE CASCADE,
                            ImageData TEXT NOT NULL,
                            FileName VARCHAR(255),
                            MimeType VARCHAR(50),
                            CreatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        );
                    """;
                    jdbcTemplate.execute(createTableSql);

                    String createArchivesSql = """
                        CREATE TABLE IF NOT EXISTS pet_archives (
                            archive_id SERIAL PRIMARY KEY,
                            original_pet_id INT,
                            pet_name VARCHAR(100) NOT NULL,
                            species VARCHAR(50),
                            breed VARCHAR(100),
                            age INT,
                            gender VARCHAR(20),
                            personality TEXT,
                            health_status TEXT,
                            image_data TEXT,
                            adopter_name VARCHAR(150),
                            adopter_email VARCHAR(150),
                            adopter_contact VARCHAR(50),
                            adoption_date DATE,
                            archive_reason VARCHAR(100) DEFAULT 'Administrative Removal',
                            archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            archived_by VARCHAR(100) DEFAULT 'Shelter Staff'
                        );
                    """;
                    jdbcTemplate.execute(createArchivesSql);

                    System.out.println(">>> Pet_Images and pet_archives tables verified and ready in PostgreSQL!");
                    ready = true;
                } catch (Exception e) {
                    if (attempt == 10) {
                        System.err.println("Notice on database tables initialization: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    // Public / Shared: Browse available pets (with clinical health records and images)
    @GetMapping("/pets")
    public List<Map<String, Object>> getAllPets() {
        String sql = """
            SELECT p.PetID, p.PetName, p.Species, p.Breed, p.Age, p.Gender, 
                   p.DateRegistered, p.AdoptionStatus,
                   pr.Personality, pr.Behavior, pr.QRCode,
                   phr.VaccinationDate, phr.MedicalCondition, phr.Medication, phr.LastCheckup, phr.NextAppointment,
                   pi.ImageData AS image_data
            FROM Pets p
            LEFT JOIN Pet_Profiles pr ON p.PetID = pr.PetID
            LEFT JOIN Pet_Health_Records phr ON p.PetID = phr.PetID
            LEFT JOIN (
                SELECT DISTINCT ON (PetID) PetID, ImageData 
                FROM Pet_Images 
                ORDER BY PetID, ImageID DESC
            ) pi ON p.PetID = pi.PetID
            ORDER BY p.PetID DESC;
        """;
        return jdbcTemplate.queryForList(sql);
    }

    // Public / Shared: Fetch single pet profile details
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable("id") int petId) {
        String sql = """
            SELECT p.PetID, p.PetName, p.Species, p.Breed, p.Age, p.Gender, 
                   p.DateRegistered, p.AdoptionStatus,
                   pr.Personality, pr.Behavior, pr.QRCode,
                   phr.VaccinationDate, phr.MedicalCondition, phr.Medication, phr.LastCheckup, phr.NextAppointment,
                   pi.ImageData AS image_data
            FROM Pets p
            LEFT JOIN Pet_Profiles pr ON p.PetID = pr.PetID
            LEFT JOIN Pet_Health_Records phr ON p.PetID = phr.PetID
            LEFT JOIN (
                SELECT DISTINCT ON (PetID) PetID, ImageData 
                FROM Pet_Images 
                ORDER BY PetID, ImageID DESC
            ) pi ON p.PetID = pi.PetID
            WHERE p.PetID = ?
            LIMIT 1;
        """;
        try {
            Map<String, Object> pet = jdbcTemplate.queryForMap(sql, petId);
            return ResponseEntity.ok(pet);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "message", "Pet #" + petId + " not found."
            ));
        }
    }

    // Shelter Operations Duty: Staff Edits Existing Pet
    @PutMapping("/pets/{id}")
    public ResponseEntity<?> updatePet(
            @PathVariable("id") int petId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", defaultValue = "adopter") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can edit pet profiles."
            ));
        }

        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Update payload cannot be empty."
            ));
        }

        try {
            Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets WHERE PetID = ?;", Integer.class, petId);
            if (exists == null || exists == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Pet #" + petId + " not found."
                ));
            }

            String petName = validateAndClampString(payload.get("pet_name"), "Pet Name", 1, 100, true, "Unnamed Companion");
            String species = validateAndClampString(payload.get("species"), "Species", 1, 50, true, "Dog");
            String breed = validateAndClampString(payload.get("breed"), "Breed", 1, 100, true, "Mixed Breed");
            int age = validateAndClampAge(payload.get("age"));
            String gender = validateAndClampString(payload.get("gender"), "Gender", 1, 20, false, "Male");
            String adoptionStatus = validateAndClampString(payload.get("adoption_status"), "Adoption Status", 1, 50, false, "Available");
            String personality = validateAndClampString(payload.get("personality"), "Personality", 0, 255, false, "Calm & Gentle (Cozy Couch Companion)");
            String behavior = validateAndClampString(payload.get("behavior"), "Behavior Notes", 0, 255, false, "Friendly companion");
            String imageData = (String) payload.get("image_data");

            java.sql.Date dateRegistered = validateAndParseDate(payload.get("date_registered"), "Date Registered");
            java.sql.Date vaccinationDate = validateAndParseDate(payload.get("vaccination_date"), "Vaccination Date");
            java.sql.Date lastCheckup = validateAndParseDate(payload.get("last_checkup"), "Last Checkup Date");
            java.sql.Date nextAppointment = validateAndParseDate(payload.get("next_appointment"), "Next Appointment Date");

            String medicalCondition = validateAndClampString(payload.get("medical_condition"), "Medical Condition", 0, 255, false, "Healthy / Cleared for Adoption");
            String medication = validateAndClampString(payload.get("medication"), "Medication", 0, 255, false, "None");

            // 1. Update Pets table
            if (dateRegistered != null) {
                jdbcTemplate.update("""
                    UPDATE Pets
                    SET PetName = ?, Species = ?, Breed = ?, Age = ?, Gender = ?, DateRegistered = ?, AdoptionStatus = ?
                    WHERE PetID = ?;
                """, petName, species, breed, age, gender, dateRegistered, adoptionStatus, petId);
            } else {
                jdbcTemplate.update("""
                    UPDATE Pets
                    SET PetName = ?, Species = ?, Breed = ?, Age = ?, Gender = ?, AdoptionStatus = ?
                    WHERE PetID = ?;
                """, petName, species, breed, age, gender, adoptionStatus, petId);
            }

            // 2. Update or insert Pet_Profiles table
            Integer profileCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pet_Profiles WHERE PetID = ?;", Integer.class, petId);
            if (profileCount != null && profileCount > 0) {
                jdbcTemplate.update("""
                    UPDATE Pet_Profiles
                    SET Personality = ?, Behavior = ?
                    WHERE PetID = ?;
                """, personality, behavior, petId);
            } else {
                String qrCodeId = "HP-PET-" + petId;
                jdbcTemplate.update("""
                    INSERT INTO Pet_Profiles (PetID, Personality, Behavior, QRCode)
                    VALUES (?, ?, ?, ?);
                """, petId, personality, behavior, qrCodeId);
            }

            // 3. Update or insert Pet_Health_Records table
            Integer healthCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pet_Health_Records WHERE PetID = ?;", Integer.class, petId);
            if (healthCount != null && healthCount > 0) {
                jdbcTemplate.update("""
                    UPDATE Pet_Health_Records
                    SET VaccinationDate = ?, MedicalCondition = ?, Medication = ?, LastCheckup = ?, NextAppointment = ?
                    WHERE PetID = ?;
                """, vaccinationDate, medicalCondition, medication, lastCheckup, nextAppointment, petId);
            } else {
                jdbcTemplate.update("""
                    INSERT INTO Pet_Health_Records (PetID, VaccinationDate, MedicalCondition, Medication, LastCheckup, NextAppointment)
                    VALUES (?, ?, ?, ?, ?, ?);
                """, petId, vaccinationDate, medicalCondition, medication, lastCheckup, nextAppointment);
            }

            // 4. Update photo if provided
            if (imageData != null && !imageData.trim().isEmpty()) {
                Integer imgCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pet_Images WHERE PetID = ?;", Integer.class, petId);
                if (imgCount != null && imgCount > 0) {
                    jdbcTemplate.update("""
                        UPDATE Pet_Images
                        SET ImageData = ?, FileName = ?, CreatedAt = CURRENT_TIMESTAMP
                        WHERE PetID = ?;
                    """, imageData, petName + "_photo.jpg", petId);
                } else {
                    jdbcTemplate.update("""
                        INSERT INTO Pet_Images (PetID, ImageData, FileName, MimeType)
                        VALUES (?, ?, ?, 'image/jpeg');
                    """, petId, imageData, petName + "_photo.jpg");
                }
            }

            if ("Adopted".equalsIgnoreCase(adoptionStatus)) {
                syncAdoptedPetsToArchives();
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Companion " + petName + " (#PET-" + petId + ") profile has been successfully updated."
            ));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", iae.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to update companion: " + e.getMessage()
            ));
        }
    }

    // Shelter Operations Duty: Only Staff can add rescued pets
    // Administrators manage users only; Adopters only browse and apply
    @PostMapping("/pets")
    public ResponseEntity<Map<String, Object>> addPet(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", defaultValue = "adopter") String requesterRole) {

        // Strict Separation of Duties Check
        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Rescued pet intake must be performed by Shelter Staff only."
            ));
        }

        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Pet intake payload cannot be empty."
            ));
        }

        try {
            String petName = validateAndClampString(payload.get("pet_name"), "Pet Name", 1, 100, true, "Unnamed Companion");
            String species = validateAndClampString(payload.get("species"), "Species", 1, 50, true, "Dog");
            String breed = validateAndClampString(payload.get("breed"), "Breed", 1, 100, true, "Mixed Breed");
            int age = validateAndClampAge(payload.get("age"));
            String gender = validateAndClampString(payload.get("gender"), "Gender", 1, 20, false, "Male");
            String personality = validateAndClampString(payload.get("personality"), "Personality", 0, 255, false, "Calm & Gentle (Cozy Couch Companion)");
            String behavior = validateAndClampString(payload.get("behavior"), "Behavior Notes", 0, 255, false, "Friendly companion");
            String imageData = (String) payload.get("image_data");

            // Parse HTML5 native date inputs safely
            java.sql.Date dateRegistered = validateAndParseDate(payload.get("date_registered"), "Date Registered");
            if (dateRegistered == null) {
                dateRegistered = new java.sql.Date(System.currentTimeMillis());
            }

            java.sql.Date vaccinationDate = validateAndParseDate(payload.get("vaccination_date"), "Vaccination Date");
            java.sql.Date lastCheckup = validateAndParseDate(payload.get("last_checkup"), "Last Checkup Date");
            java.sql.Date nextAppointment = validateAndParseDate(payload.get("next_appointment"), "Next Appointment Date");

            String medicalCondition = validateAndClampString(payload.get("medical_condition"), "Medical Condition", 0, 255, false, "Healthy / Routine Intake");
            String medication = validateAndClampString(payload.get("medication"), "Medication", 0, 255, false, "None");

            // 1. Insert into Pets
            String insertPetSql = """
                INSERT INTO Pets (PetName, Species, Breed, Age, Gender, DateRegistered, AdoptionStatus)
                VALUES (?, ?, ?, ?, ?, ?, 'Available')
                RETURNING PetID;
            """;
            Integer newPetId = jdbcTemplate.queryForObject(insertPetSql, Integer.class, petName, species, breed, age, gender, dateRegistered);

            // 2. Insert into Pet_Profiles (with QR code)
            String qrCodeId = "HP-PET-" + newPetId;
            String insertProfileSql = """
                INSERT INTO Pet_Profiles (PetID, Personality, Behavior, QRCode)
                VALUES (?, ?, ?, ?);
            """;
            jdbcTemplate.update(insertProfileSql, newPetId, personality, behavior, qrCodeId);

            // 3. Insert into Pet_Health_Records
            String insertHealthSql = """
                INSERT INTO Pet_Health_Records (PetID, VaccinationDate, MedicalCondition, Medication, LastCheckup, NextAppointment)
                VALUES (?, ?, ?, ?, ?, ?);
            """;
            jdbcTemplate.update(insertHealthSql, newPetId, vaccinationDate, medicalCondition, medication, lastCheckup, nextAppointment);

            // 4. Save photo into Pet_Images if provided
            if (imageData != null && !imageData.trim().isEmpty()) {
                try {
                    String insertImgSql = """
                        INSERT INTO Pet_Images (PetID, ImageData, FileName, MimeType)
                        VALUES (?, ?, ?, 'image/jpeg');
                    """;
                    jdbcTemplate.update(insertImgSql, newPetId, imageData, petName + "_photo.jpg");
                } catch (Exception e) {
                    System.err.println("Notice inserting into Pet_Images: " + e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("petId", newPetId);
            result.put("message", "Pet " + petName + " registered into Happy Paws roster with health records and QR code generated.");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", iae.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to register pet: " + e.getMessage()
            ));
        }
    }

    // Direct Image Upload for any pet
    @PostMapping("/pets/{id}/image")
    public ResponseEntity<?> uploadPetImage(
            @PathVariable("id") int petId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can upload companion photos."
            ));
        }

        String imageData = (String) payload.get("image_data");
        if (imageData == null || imageData.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Image data is required."));
        }

        try {
            jdbcTemplate.update("DELETE FROM Pet_Images WHERE PetID = ?;", petId);
            jdbcTemplate.update("INSERT INTO Pet_Images (PetID, ImageData, MimeType) VALUES (?, ?, 'image/jpeg');", petId, imageData);
            return ResponseEntity.ok(Map.of("success", true, "message", "Photo uploaded and saved to database for pet #" + petId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Staff Updates Pet Health Records
    @PutMapping("/pets/{id}/health")
    public ResponseEntity<?> updatePetHealth(
            @PathVariable("id") int petId,
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can update medical records."
            ));
        }

        String vaccinationDateStr = (String) payload.get("vaccination_date");
        java.sql.Date vaccinationDate = null;
        if (vaccinationDateStr != null && !vaccinationDateStr.trim().isEmpty()) {
            try { vaccinationDate = java.sql.Date.valueOf(vaccinationDateStr.trim()); } catch (Exception ignored) {}
        }

        String lastCheckupStr = (String) payload.get("last_checkup");
        java.sql.Date lastCheckup = null;
        if (lastCheckupStr != null && !lastCheckupStr.trim().isEmpty()) {
            try { lastCheckup = java.sql.Date.valueOf(lastCheckupStr.trim()); } catch (Exception ignored) {}
        }

        String nextAppointmentStr = (String) payload.get("next_appointment");
        java.sql.Date nextAppointment = null;
        if (nextAppointmentStr != null && !nextAppointmentStr.trim().isEmpty()) {
            try { nextAppointment = java.sql.Date.valueOf(nextAppointmentStr.trim()); } catch (Exception ignored) {}
        }

        String medicalCondition = (String) payload.getOrDefault("medical_condition", "Healthy");
        String medication = (String) payload.getOrDefault("medication", "None");

        int updated = jdbcTemplate.update("""
            UPDATE Pet_Health_Records
            SET VaccinationDate = ?, MedicalCondition = ?, Medication = ?, LastCheckup = ?, NextAppointment = ?
            WHERE PetID = ?;
        """, vaccinationDate, medicalCondition, medication, lastCheckup, nextAppointment, petId);

        if (updated == 0) {
            jdbcTemplate.update("""
                INSERT INTO Pet_Health_Records (PetID, VaccinationDate, MedicalCondition, Medication, LastCheckup, NextAppointment)
                VALUES (?, ?, ?, ?, ?, ?);
            """, petId, vaccinationDate, medicalCondition, medication, lastCheckup, nextAppointment);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Health records updated successfully."));
    }

    // Dashboard Metrics: Restricted to Shelter Staff
    @GetMapping("/stats")
    public ResponseEntity<?> getDashboardStats(@RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {
        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Access denied: Shelter Dashboard is reserved for Shelter Staff."
            ));
        }

        Map<String, Object> stats = new HashMap<>();

        // Pets currently residing in shelter care (excluding pets that have already found forever homes)
        Integer petsInCare = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets WHERE LOWER(AdoptionStatus) != 'adopted';", Integer.class);
        Integer availablePets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets WHERE LOWER(AdoptionStatus) = 'available';", Integer.class);
        Integer adoptedPets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets WHERE LOWER(AdoptionStatus) = 'adopted';", Integer.class);
        Integer totalRescued = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets;", Integer.class);

        Integer pendingApps = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Adoption_Applications WHERE Status = 'Pending';", Integer.class);
        Integer approvedApps = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Adoption_Applications WHERE Status = 'Approved';", Integer.class);
        Integer rejectedApps = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Adoption_Applications WHERE Status = 'Rejected';", Integer.class);

        List<Map<String, Object>> speciesCounts = jdbcTemplate.queryForList("""
            SELECT Species, COUNT(*) AS count 
            FROM Pets 
            WHERE LOWER(AdoptionStatus) != 'adopted'
            GROUP BY Species 
            ORDER BY count DESC;
        """);

        List<Map<String, Object>> monthlyAdoptions = jdbcTemplate.queryForList("""
            SELECT TO_CHAR(AdoptionDate, 'Mon') AS month_label,
                   EXTRACT(MONTH FROM AdoptionDate) AS month_num,
                   COUNT(*) AS count
            FROM Adoptions
            GROUP BY month_label, month_num
            ORDER BY month_num;
        """);

        stats.put("petsInCare", petsInCare != null ? petsInCare : 0);
        stats.put("totalPets", petsInCare != null ? petsInCare : 0);
        stats.put("totalRescued", totalRescued != null ? totalRescued : 0);
        stats.put("availablePets", availablePets != null ? availablePets : 0);
        stats.put("adoptedPets", adoptedPets != null ? adoptedPets : 0);
        stats.put("pendingApps", pendingApps != null ? pendingApps : 0);
        stats.put("approvedApps", approvedApps != null ? approvedApps : 0);
        stats.put("rejectedApps", rejectedApps != null ? rejectedApps : 0);
        stats.put("speciesCounts", speciesCounts);
        stats.put("monthlyAdoptions", monthlyAdoptions);

        return ResponseEntity.ok(stats);
    }

    // Clinical / Veterinary Duty: Restricted to Shelter Staff
    @GetMapping("/health")
    public ResponseEntity<?> getHealthRecords(@RequestHeader(value = "X-User-Role", defaultValue = "adopter") String requesterRole) {
        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Medical health records are restricted to Shelter Staff."
            ));
        }

        String sql = """
            SELECT h.HealthID, h.PetID, p.PetName, p.Species, p.Breed,
                   h.VaccinationDate, h.MedicalCondition, h.Medication,
                   h.LastCheckup, h.NextAppointment
            FROM Pet_Health_Records h
            JOIN Pets p ON h.PetID = p.PetID
            ORDER BY h.HealthID DESC;
        """;
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // Option B: Adopters List (Staff duty) with UserID linkage
    @GetMapping("/adopters")
    public ResponseEntity<?> getAdopters(@RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {
        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Adopter directory is restricted to Shelter Staff."
            ));
        }

        String sql = """
            SELECT a.AdopterID, a.UserID,
                   COALESCE(u.FullName, 'Adopter Profile') AS FullName,
                   COALESCE(u.Email, 'No email') AS Email,
                   a.ContactNo, a.Address, a.Status
            FROM Adopters a
            LEFT JOIN Users u ON a.UserID = u.UserID
            ORDER BY a.AdopterID DESC;
        """;
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
    }

    // Option B: Adoption Applications List with UserID profile linkage
    @GetMapping("/applications")
    public List<Map<String, Object>> getApplications() {
        String sql = """
            SELECT app.ApplicationID, app.ApplicationDate, app.Status, app.ReviewDate,
                   app.LivingEnvironment, app.Notes,
                   COALESCE(u.FullName, 'Adopter Applicant') AS AdopterName,
                   COALESCE(u.Email, 'No email') AS AdopterEmail,
                   ad.ContactNo, ad.UserID,
                   p.PetName, p.Species, p.Breed, p.PetID,
                   pr.Personality
            FROM Adoption_Applications app
            LEFT JOIN Adopters ad ON app.AdopterID = ad.AdopterID
            LEFT JOIN Users u ON ad.UserID = u.UserID
            LEFT JOIN Pets p ON app.PetID = p.PetID
            LEFT JOIN Pet_Profiles pr ON p.PetID = pr.PetID
            ORDER BY app.ApplicationID DESC;
        """;
        return jdbcTemplate.queryForList(sql);
    }

    // Option B: Adopter submits application - creates or links Adopters profile with UserID
    @PostMapping("/applications")
    public ResponseEntity<Map<String, Object>> submitApplication(@RequestBody Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Application payload cannot be empty."
            ));
        }

        try {
            if (payload.get("petId") == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please select a valid companion to adopt."
                ));
            }

            int petId;
            try {
                petId = Integer.parseInt(payload.get("petId").toString().replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid companion ID provided."
                ));
            }

            Integer petExists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets WHERE PetID = ?;", Integer.class, petId);
            if (petExists == null || petExists == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Companion #" + petId + " does not exist in shelter records."
                ));
            }

            String userEmail = ((String) payload.getOrDefault("email", "")).trim().toLowerCase();
            if (userEmail.isEmpty() || !userEmail.contains("@") || userEmail.length() > 100) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "A valid applicant email address is required."
                ));
            }

            String environment = validateAndClampString(payload.get("environment"), "Living Environment", 1, 200, false, "Apartment / Quiet Living Space");
            String notes = validateAndClampString(payload.get("notes"), "Household Notes", 0, 500, false, environment);

            // Find associated user account in Users table
            Integer userId = null;
            try {
                List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT UserID FROM Users WHERE LOWER(Email) = ? LIMIT 1;",
                    userEmail
                );
                if (!users.isEmpty()) {
                    userId = ((Number) users.get(0).get("userid")).intValue();
                }
            } catch (Exception ignored) {}

            // Find or create Adopters profile linked by UserID
            Integer adopterId = null;
            if (userId != null) {
                try {
                    List<Map<String, Object>> adopters = jdbcTemplate.queryForList(
                        "SELECT AdopterID FROM Adopters WHERE UserID = ? LIMIT 1;",
                        userId
                    );
                    if (!adopters.isEmpty()) {
                        adopterId = ((Number) adopters.get(0).get("adopterid")).intValue();
                    }
                } catch (Exception ignored) {}
            }

            if (adopterId == null && userId != null) {
                try {
                    List<Map<String, Object>> newRows = jdbcTemplate.queryForList("""
                        INSERT INTO Adopters (UserID, Status)
                        VALUES (?, 'Active')
                        RETURNING AdopterID;
                    """, userId);
                    if (!newRows.isEmpty()) {
                        adopterId = ((Number) newRows.get(0).get("adopterid")).intValue();
                    }
                } catch (Exception ex) {
                    System.out.println("Adopter profile creation fallback: " + ex.getMessage());
                }
            }

            // Insert into Adoption_Applications
            Integer appId = null;
            try {
                List<Map<String, Object>> appRows = jdbcTemplate.queryForList("""
                    INSERT INTO Adoption_Applications (AdopterID, PetID, Status, ApplicationDate, LivingEnvironment, Notes)
                    VALUES (?, ?, 'Pending', CURRENT_DATE, ?, ?)
                    RETURNING ApplicationID;
                """, adopterId, petId, environment, notes);
                if (!appRows.isEmpty()) {
                    appId = ((Number) appRows.get(0).get("applicationid")).intValue();
                }
            } catch (Exception ex) {
                try {
                    List<Map<String, Object>> appRows = jdbcTemplate.queryForList("""
                        INSERT INTO Adoption_Applications (AdopterID, PetID, Status, ApplicationDate)
                        VALUES (?, ?, 'Pending', CURRENT_DATE)
                        RETURNING ApplicationID;
                    """, adopterId, petId);
                    if (!appRows.isEmpty()) {
                        appId = ((Number) appRows.get(0).get("applicationid")).intValue();
                    }
                } catch (Exception ignored) {}
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "applicationId", appId != null ? appId : 1,
                "message", "Adoption application submitted for review by shelter staff."
            ));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", iae.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to submit adoption application: " + e.getMessage()
            ));
        }
    }

    // Shelter Operations Duty: Approve or Reject Application (Shelter Staff only)
    @PutMapping("/applications/{id}/status")
    public ResponseEntity<Map<String, Object>> updateApplicationStatus(
            @PathVariable int id,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "X-User-Role", defaultValue = "adopter") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Application reviews must be conducted by Shelter Staff only."
            ));
        }

        String status = payload.getOrDefault("status", "Pending");
        if (!status.equals("Approved") && !status.equals("Rejected") && !status.equals("Pending")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid status value."));
        }

        jdbcTemplate.update(
            "UPDATE Adoption_Applications SET Status = ?, ReviewDate = CURRENT_DATE WHERE ApplicationID = ?;",
            status, id
        );

        // If approved, update pet status and create Adoptions row (streamlined schema)
        if ("Approved".equalsIgnoreCase(status)) {
            try {
                Map<String, Object> app = jdbcTemplate.queryForMap(
                    "SELECT PetID FROM Adoption_Applications WHERE ApplicationID = ?;",
                    id
                );
                int petId = ((Number) app.get("petid")).intValue();

                jdbcTemplate.update("UPDATE Pets SET AdoptionStatus = 'Adopted' WHERE PetID = ?;", petId);
                jdbcTemplate.update(
                    "INSERT INTO Adoptions (ApplicationID, AdoptionDate, AdoptionStatus) VALUES (?, CURRENT_DATE, 'Completed');",
                    id
                );
                syncAdoptedPetsToArchives();
            } catch (Exception ignored) {}
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Application status updated to " + status + "."
        ));
    }

    // Option B: Staff Archives a Pet (Safe Deletion with Adopter Audit Trail)
    @PostMapping("/pets/{id}/archive")
    public ResponseEntity<?> archivePet(
            @PathVariable int id,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can archive pet records."
            ));
        }

        try {
            // 1. Fetch current pet info including clinical records and photo
            List<Map<String, Object>> pets = jdbcTemplate.queryForList("""
                SELECT p.PetID, p.PetName, p.Species, p.Breed, p.Age, p.Gender, p.AdoptionStatus,
                       pr.Personality, pr.Behavior,
                       phr.MedicalCondition, phr.Medication, phr.VaccinationDate,
                       pi.ImageData
                FROM Pets p
                LEFT JOIN Pet_Profiles pr ON p.PetID = pr.PetID
                LEFT JOIN Pet_Health_Records phr ON p.PetID = phr.PetID
                LEFT JOIN (
                    SELECT DISTINCT ON (PetID) PetID, ImageData 
                    FROM Pet_Images 
                    ORDER BY PetID, ImageID DESC
                ) pi ON p.PetID = pi.PetID
                WHERE p.PetID = ?;
            """, id);

            if (pets.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Pet #" + id + " not found."
                ));
            }

            Map<String, Object> pet = pets.get(0);
            String petName = (String) pet.get("petname");
            String species = (String) pet.get("species");
            String breed = (String) pet.get("breed");
            Integer age = pet.get("age") != null ? ((Number) pet.get("age")).intValue() : 1;
            String gender = (String) pet.get("gender");
            String personality = (String) pet.get("personality");
            String medicalCondition = (String) pet.get("medicalcondition");
            String medication = (String) pet.get("medication");
            Object vaxDate = pet.get("vaccinationdate");
            String healthStatus = "Condition: " + (medicalCondition != null ? medicalCondition : "Normal") + 
                                  " | Meds: " + (medication != null ? medication : "None") + 
                                  " | Vax: " + (vaxDate != null ? vaxDate.toString() : "Not recorded");
            String imageData = (String) pet.get("imagedata");
            String adoptionStatus = (String) pet.get("adoptionstatus");

            // 2. Fetch adopter info if adopted
            String adopterName = "Unadopted / In Shelter";
            String adopterEmail = "-";
            String adopterContact = "-";
            java.sql.Date adoptionDate = null;

            List<Map<String, Object>> adoptionRecords = jdbcTemplate.queryForList("""
                SELECT COALESCE(u.FullName, 'Adopter') AS adopter_name,
                       COALESCE(u.Email, '-') AS adopter_email,
                       ad.ContactNo, a.AdoptionDate
                FROM Adoptions a
                JOIN Adoption_Applications app ON a.ApplicationID = app.ApplicationID
                JOIN Adopters ad ON app.AdopterID = ad.AdopterID
                LEFT JOIN Users u ON ad.UserID = u.UserID
                WHERE app.PetID = ?
                ORDER BY a.AdoptionID DESC LIMIT 1;
            """, id);

            if (!adoptionRecords.isEmpty()) {
                Map<String, Object> adRec = adoptionRecords.get(0);
                if (adRec.get("adopter_name") != null) adopterName = (String) adRec.get("adopter_name");
                if (adRec.get("adopter_email") != null) adopterEmail = (String) adRec.get("adopter_email");
                if (adRec.get("contactno") != null) adopterContact = (String) adRec.get("contactno");
                Object rawDate = adRec.get("adoptiondate");
                if (rawDate instanceof java.sql.Date d) adoptionDate = d;
                else if (rawDate instanceof java.time.LocalDate ld) adoptionDate = java.sql.Date.valueOf(ld);
                else if (rawDate != null) {
                    try { adoptionDate = java.sql.Date.valueOf(rawDate.toString().split("T")[0]); } catch (Exception ignored) {}
                }
            } else {
                List<Map<String, Object>> appRecords = jdbcTemplate.queryForList("""
                    SELECT COALESCE(u.FullName, 'Adopter') AS adopter_name,
                           COALESCE(u.Email, '-') AS adopter_email,
                           ad.ContactNo, app.ReviewDate AS adoption_date
                    FROM Adoption_Applications app
                    JOIN Adopters ad ON app.AdopterID = ad.AdopterID
                    LEFT JOIN Users u ON ad.UserID = u.UserID
                    WHERE app.PetID = ? AND app.Status = 'Approved'
                    ORDER BY app.ApplicationID DESC LIMIT 1;
                """, id);
                if (!appRecords.isEmpty()) {
                    Map<String, Object> appRec = appRecords.get(0);
                    if (appRec.get("adopter_name") != null) adopterName = (String) appRec.get("adopter_name");
                    if (appRec.get("adopter_email") != null) adopterEmail = (String) appRec.get("adopter_email");
                    if (appRec.get("contactno") != null) adopterContact = (String) appRec.get("contactno");
                    Object rawDate = appRec.get("adoption_date");
                    if (rawDate instanceof java.sql.Date d) adoptionDate = d;
                    else if (rawDate instanceof java.time.LocalDate ld) adoptionDate = java.sql.Date.valueOf(ld);
                    else if (rawDate != null) {
                        try { adoptionDate = java.sql.Date.valueOf(rawDate.toString().split("T")[0]); } catch (Exception ignored) {}
                    }
                }
            }

            String customReason = (reason != null && !reason.trim().isEmpty())
                    ? reason.trim()
                    : ("Adopted".equalsIgnoreCase(adoptionStatus) ? "Successfully Adopted" : "Administrative Removal");

            // 3. Insert into pet_archives
            jdbcTemplate.update("""
                INSERT INTO pet_archives (
                    original_pet_id, pet_name, species, breed, age, gender,
                    personality, health_status, image_data,
                    adopter_name, adopter_email, adopter_contact, adoption_date,
                    archive_reason, archived_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Shelter Staff');
            """, id, petName, species, breed, age, gender, personality, healthStatus, imageData,
                 adopterName, adopterEmail, adopterContact, adoptionDate, customReason);

            // 4. Delete pet from Pets (cascades to Pet_Profiles, Pet_Health_Records, Pet_Images, etc.)
            jdbcTemplate.update("DELETE FROM Pets WHERE PetID = ?;", id);

            // Re-sync Pets sequence so reclaimed/deleted IDs can be immediately reused
            try {
                jdbcTemplate.execute("""
                    SELECT setval(
                        pg_get_serial_sequence('pets', 'petid'), 
                        COALESCE((SELECT MAX(petid) FROM pets), 0) + 1, 
                        false
                    );
                """);
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Companion " + petName + " (#PET-" + id + ") has been moved to Archives."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to archive pet: " + e.getMessage()
            ));
        }
    }

    // Option B: Staff Restores an Archived Pet (Recovery CRUD operation)
    @PostMapping("/archives/{archiveId}/restore")
    public ResponseEntity<?> restorePet(
            @PathVariable int archiveId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can restore archived pets."
            ));
        }

        try {
            List<Map<String, Object>> archives = jdbcTemplate.queryForList("""
                SELECT * FROM pet_archives WHERE archive_id = ?;
            """, archiveId);

            if (archives.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Archive record #" + archiveId + " not found."
                ));
            }

            Map<String, Object> arch = archives.get(0);
            String petName = (String) arch.get("pet_name");
            String species = (String) arch.get("species");
            String breed = (String) arch.get("breed");
            Integer age = arch.get("age") != null ? ((Number) arch.get("age")).intValue() : 1;
            String gender = (String) arch.get("gender");
            String personality = (String) arch.get("personality");
            String healthStatus = (String) arch.get("health_status");
            String imageData = (String) arch.get("image_data");

            Integer originalPetId = arch.get("original_pet_id") != null ? ((Number) arch.get("original_pet_id")).intValue() : null;
            Integer newPetId = null;

            if (originalPetId != null) {
                Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Pets WHERE PetID = ?;", Integer.class, originalPetId);
                if (exists != null && exists > 0) {
                    jdbcTemplate.update("UPDATE Pets SET AdoptionStatus = 'Available' WHERE PetID = ?;", originalPetId);
                    newPetId = originalPetId;
                }
            }

            if (newPetId == null) {
                // Re-insert into Pets
                String insertPetSql = """
                    INSERT INTO Pets (PetName, Species, Breed, Age, Gender, AdoptionStatus, DateRegistered)
                    VALUES (?, ?, ?, ?, ?, 'Available', CURRENT_DATE)
                    RETURNING PetID;
                """;
                newPetId = jdbcTemplate.queryForObject(insertPetSql, Integer.class, petName, species, breed, age, gender);

                // Re-insert into Pet_Profiles
                jdbcTemplate.update("""
                    INSERT INTO Pet_Profiles (PetID, Personality, Behavior, QRCode)
                    VALUES (?, ?, 'Friendly companion', ?);
                """, newPetId, personality != null ? personality : "Playful and friendly", "HP-PET-" + newPetId);

                // Re-insert into Pet_Health_Records
                jdbcTemplate.update("""
                    INSERT INTO Pet_Health_Records (PetID, MedicalCondition, Medication)
                    VALUES (?, ?, 'None');
                """, newPetId, healthStatus != null ? healthStatus : "Healthy");

                // Re-insert into Pet_Images
                if (imageData != null && !imageData.trim().isEmpty()) {
                    try {
                        jdbcTemplate.update("""
                            INSERT INTO Pet_Images (PetID, ImageData, FileName, MimeType)
                            VALUES (?, ?, ?, 'image/jpeg');
                        """, newPetId, imageData, petName + "_restored.jpg");
                    } catch (Exception ignored) {}
                }
            }

            // Remove from pet_archives
            jdbcTemplate.update("DELETE FROM pet_archives WHERE archive_id = ?;", archiveId);

            // Re-sync archive ID sequence
            try {
                jdbcTemplate.execute("""
                    SELECT setval(
                        pg_get_serial_sequence('pet_archives', 'archive_id'), 
                        COALESCE((SELECT MAX(archive_id) FROM pet_archives), 0) + 1, 
                        false
                    );
                """);
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of(
                "success", true,
                "petId", newPetId,
                "message", "Companion " + petName + " has been recovered and restored to active catalog as #PET-" + newPetId + "!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to restore companion: " + e.getMessage()
            ));
        }
    }

    // Option B: Staff Permanently Deletes an Archive Record (Freeing Sequence ID)
    @DeleteMapping("/archives/{archiveId}")
    public ResponseEntity<?> deleteArchive(
            @PathVariable int archiveId,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can delete archive records."
            ));
        }

        try {
            List<Map<String, Object>> recs = jdbcTemplate.queryForList("SELECT original_pet_id FROM pet_archives WHERE archive_id = ?;", archiveId);
            Integer origPetId = (!recs.isEmpty() && recs.get(0).get("original_pet_id") != null) 
                    ? ((Number) recs.get(0).get("original_pet_id")).intValue() 
                    : null;

            int deleted = jdbcTemplate.update("DELETE FROM pet_archives WHERE archive_id = ?;", archiveId);
            if (deleted == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", "Archive record #" + archiveId + " not found."
                ));
            }

            if (origPetId != null) {
                try {
                    jdbcTemplate.update("DELETE FROM Adoptions WHERE ApplicationID IN (SELECT ApplicationID FROM Adoption_Applications WHERE PetID = ?);", origPetId);
                    jdbcTemplate.update("DELETE FROM Adoption_Applications WHERE PetID = ?;", origPetId);
                    jdbcTemplate.update("DELETE FROM Pet_Images WHERE PetID = ?;", origPetId);
                    jdbcTemplate.update("DELETE FROM Pet_Health_Records WHERE PetID = ?;", origPetId);
                    jdbcTemplate.update("DELETE FROM Pet_Profiles WHERE PetID = ?;", origPetId);
                    jdbcTemplate.update("DELETE FROM Pets WHERE PetID = ?;", origPetId);

                    jdbcTemplate.execute("""
                        SELECT setval(
                            pg_get_serial_sequence('pets', 'petid'), 
                            COALESCE((SELECT MAX(petid) FROM pets), 0) + 1, 
                            false
                        );
                    """);
                } catch (Exception ignored) {}
            }

            // Re-sync archive ID sequence so deleted ID numbers are immediately freed for future records
            try {
                jdbcTemplate.execute("""
                    SELECT setval(
                        pg_get_serial_sequence('pet_archives', 'archive_id'), 
                        COALESCE((SELECT MAX(archive_id) FROM pet_archives), 0) + 1, 
                        false
                    );
                """);
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Archive record #" + archiveId + " has been permanently deleted and its ID freed."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to delete archive record: " + e.getMessage()
            ));
        }
    }

    // Option B: Staff Batch / Multi-Select Permanent Archive Deletion
    @PostMapping("/archives/bulk-delete")
    public ResponseEntity<?> bulkDeleteArchives(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Separation of Duties violation: Only Shelter Staff can delete archive records."
            ));
        }

        Object rawIds = payload.get("archiveIds");
        if (!(rawIds instanceof List<?> list) || list.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "No archive records selected for deletion."
            ));
        }

        List<Integer> ids = list.stream()
                .map(o -> Integer.parseInt(o.toString()))
                .toList();

        try {
            String inSql = String.join(",", ids.stream().map(String::valueOf).toList());
            int deleted = jdbcTemplate.update("DELETE FROM pet_archives WHERE archive_id IN (" + inSql + ");");

            // Re-sync archive ID sequence
            try {
                jdbcTemplate.execute("""
                    SELECT setval(
                        pg_get_serial_sequence('pet_archives', 'archive_id'), 
                        COALESCE((SELECT MAX(archive_id) FROM pet_archives), 0) + 1, 
                        false
                    );
                """);
            } catch (Exception ignored) {}

            return ResponseEntity.ok(Map.of(
                "success", true,
                "deletedCount", deleted,
                "message", deleted + " archived companion record(s) permanently deleted and IDs freed."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to delete archive records: " + e.getMessage()
            ));
        }
    }

    // Shelter Operations Helper: Synchronize Successfully Adopted pets into pet_archives
    private void syncAdoptedPetsToArchives() {
        try {
            String syncSql = """
                INSERT INTO pet_archives (
                    original_pet_id, pet_name, species, breed, age, gender,
                    personality, health_status, image_data,
                    adopter_name, adopter_email, adopter_contact, adoption_date,
                    archive_reason, archived_by
                )
                SELECT DISTINCT ON (p.PetID)
                    p.PetID,
                    p.PetName,
                    p.Species,
                    p.Breed,
                    p.Age,
                    p.Gender,
                    pr.Personality,
                    CONCAT(
                        'Condition: ', COALESCE(phr.MedicalCondition, 'Healthy / Cleared for Adoption'),
                        ' | Meds: ', COALESCE(phr.Medication, 'None'),
                        ' | Vax: ', COALESCE(phr.VaccinationDate::TEXT, 'Not recorded')
                    ),
                    pi.ImageData,
                    COALESCE(u.FullName, 'Adopter'),
                    COALESCE(u.Email, '-'),
                    COALESCE(ad.ContactNo, '-'),
                    COALESCE(a.AdoptionDate, app.ReviewDate, CURRENT_DATE),
                    'Successfully Adopted',
                    'Shelter Staff'
                FROM Pets p
                LEFT JOIN Adoption_Applications app ON p.PetID = app.PetID AND app.Status = 'Approved'
                LEFT JOIN Adoptions a ON app.ApplicationID = a.ApplicationID
                LEFT JOIN Adopters ad ON app.AdopterID = ad.AdopterID
                LEFT JOIN Users u ON ad.UserID = u.UserID
                LEFT JOIN Pet_Profiles pr ON p.PetID = pr.PetID
                LEFT JOIN Pet_Health_Records phr ON p.PetID = phr.PetID
                LEFT JOIN (
                    SELECT DISTINCT ON (PetID) PetID, ImageData 
                    FROM Pet_Images 
                    ORDER BY PetID, ImageID DESC
                ) pi ON p.PetID = pi.PetID
                WHERE p.AdoptionStatus = 'Adopted'
                  AND NOT EXISTS (
                      SELECT 1 FROM pet_archives pa 
                      WHERE pa.original_pet_id = p.PetID 
                        AND pa.archive_reason ILIKE '%adopt%'
                  )
                ORDER BY p.PetID, a.AdoptionID DESC NULLS LAST, app.ApplicationID DESC NULLS LAST;
            """;
            jdbcTemplate.execute(syncSql);
        } catch (Exception e) {
            System.err.println("Notice: syncAdoptedPetsToArchives: " + e.getMessage());
        }
    }

    // Option B: Staff View Archives & Adoption History
    @GetMapping("/archives")
    public ResponseEntity<?> getArchives(
            @RequestHeader(value = "X-User-Role", defaultValue = "") String requesterRole) {

        if (!"staff".equalsIgnoreCase(requesterRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "Access denied: Archives directory is restricted to Shelter Staff."
            ));
        }

        try {
            syncAdoptedPetsToArchives();

            String sql = """
                SELECT archive_id, original_pet_id, pet_name, species, breed, age, gender,
                       personality, health_status, image_data,
                       adopter_name, adopter_email, adopter_contact, adoption_date,
                       archive_reason, archived_at, archived_by
                FROM pet_archives
                ORDER BY archive_id DESC;
            """;
            return ResponseEntity.ok(jdbcTemplate.queryForList(sql));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }
}

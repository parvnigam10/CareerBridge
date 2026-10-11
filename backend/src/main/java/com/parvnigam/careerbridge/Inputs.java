package com.parvnigam.careerbridge;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class Inputs {
    public record VerificationEmail(@Email @NotBlank @Size(max=254) String email) {}
    public record VerifyEmail(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
    public record ResetPassword(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(min=10,max=72) String newPassword,@NotBlank @Size(min=10,max=72) String confirmPassword) {}
    public record SubmitAcademics(@NotNull @Size(min=3,max=8) List<@NotNull @Positive Long> documentIds,
        @NotNull @Min(0) Long academicRevision,@Size(max=1000) String note) {}
    public record ReviewAcademics(@NotNull @Min(0) Long version,@NotBlank @Pattern(regexp="VERIFIED|CHANGES_REQUESTED") String status,
        @Size(max=2000) String note,@AssertTrue boolean documentsChecked) {}
    public record Login(@Email @NotBlank @Size(max=254) String email,@NotBlank @Size(max=72) String password) {}
    public record Register(@NotBlank @Size(max=80) String name,@Email @NotBlank @Size(max=254) String email,
        @NotBlank @Size(min=10,max=72) String password,@NotNull Model.Role role,Long universityId,@Size(max=100) String department,@Min(1960) @Max(2100) Integer graduationYear) {}
    public record Profile(@Size(max=140) String headline,@Size(max=4000) String about,@Size(max=150) String city,
        @DecimalMin("0.0") @DecimalMax("10.0") Double cgpa,@DecimalMin("0.0") Double experienceYears,
        @Size(max=140) String preferredRole,@Size(max=39) String githubUsername,@Size(max=50) String leetcodeUsername,boolean visibleToRecruiters,
        @Size(max=40) List<@Size(max=60) String> skills) {}
    public record Job(@NotBlank @Size(max=140) String title,@NotBlank @Size(max=12000) String description,
        @NotBlank @Size(max=150) String location,@NotBlank String employmentType,@NotBlank String workMode,
        @Min(0) Integer salaryMin,@Min(0) Integer salaryMax,@Min(1) Integer openings,
        @DecimalMin("0.0") @DecimalMax("10.0") Double minCgpa,@DecimalMin("0.0") Double minExperience,
        @FutureOrPresent LocalDate deadline,Long universityId,@NotNull @Size(min=1,max=40) List<@NotBlank @Size(max=60) String> requiredSkills,
        @DecimalMin("0.0") @DecimalMax("100.0") Double minTenthPercentage,
        @DecimalMin("0.0") @DecimalMax("100.0") Double minSecondaryPercentage,
        @Min(0) @Max(50) Integer maxActiveBacklogs,
        @Size(max=30) List<@NotBlank @Size(max=100) String> allowedBranches,
        @Size(max=30) List<@NotNull @Min(1960) @Max(2100) Integer> allowedGraduationYears) {}
    public record Project(@NotBlank @Size(max=140) String title,@Size(max=4000) String description,
        @Size(max=200) String stack,@Size(max=2048) String repositoryUrl,@Size(max=2048) String liveUrl) {}
    public record Academics(
        @Size(max=200) String tenthInstitution, @Size(max=100) String tenthBoard,
        @Min(1960) @Max(2100) Integer tenthYear, @Pattern(regexp="PERCENTAGE|CGPA|GRADE") String tenthScoreType,
        @DecimalMin("0.0") @DecimalMax("100.0") Double tenthScore,
        @DecimalMin(value="0.0",inclusive=false) @DecimalMax("10.0") Double tenthScale, @Size(max=30) String tenthGrade,
        @Pattern(regexp="TWELFTH|DIPLOMA") String secondaryQualification,
        @Size(max=200) String secondaryInstitution, @Size(max=100) String secondaryBoard,
        @Min(1960) @Max(2100) Integer secondaryYear, @Pattern(regexp="PERCENTAGE|CGPA|GRADE") String secondaryScoreType,
        @DecimalMin("0.0") @DecimalMax("100.0") Double secondaryScore,
        @DecimalMin(value="0.0",inclusive=false) @DecimalMax("10.0") Double secondaryScale, @Size(max=30) String secondaryGrade,
        @Size(max=200) String collegeInstitution, @Size(max=140) String degree, @Size(max=100) String department,
        @Min(1960) @Max(2100) Integer graduationYear, @Min(1) @Max(16) Integer currentSemester,
        @DecimalMin("0.0") @DecimalMax("10.0") Double cgpa,
        @Min(0) @Max(50) Integer activeBacklogs, @Min(0) @Max(50) Integer clearedBacklogs,
        @Min(0) @Max(20) Integer gapYears) {}
    public record Education(@NotBlank @Size(max=200) String institution,@NotBlank @Size(max=140) String qualification,@Min(1960) @Max(2100) Integer passingYear,@Size(max=60) String grade) {}
    public record Experience(@NotBlank @Size(max=140) String company,@NotBlank @Size(max=140) String title,@Size(max=140) String duration,@Size(max=4000) String description) {}
    public record Apply(@NotNull Long jobId,@Size(max=4000) String coverLetter) {}
    public record Stage(@NotNull Model.Stage status,@Size(max=2000) String note) {}
    public record Interview(@NotNull @Future Instant scheduledAt,@Min(15) @Max(180) int durationMinutes,@Size(max=2048) String meetingUrl,@Size(max=2000) String instructions) {}
    public record Company(@NotBlank @Size(max=140) String name,@Size(max=2048) String website,@Size(max=150) String location,@Size(max=4000) String description) {}
    public record Mock(@NotBlank @Size(max=150) String targetRole,@NotBlank @Size(max=150) String skill) {}
}

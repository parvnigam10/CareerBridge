package com.parvnigam.careerbridge;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

public final class Model {
    private Model() {}
    public enum Role { STUDENT, RECRUITER, PLACEMENT_OFFICER, ADMIN }
    public enum Stage { APPLIED, SHORTLISTED, INTERVIEW, OFFERED, HIRED, REJECTED, WITHDRAWN }
    @MappedSuperclass
    public static class Base {
        @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
        @Version public long version;
        public Instant createdAt = Instant.now();
    }
    @Entity(name="Account") @Table(name="accounts")
    public static class Account extends Base {
        @Column(nullable=false,unique=true) public String email;
        @Column(nullable=false) public String passwordHash;
        public String name;
        @Enumerated(EnumType.STRING) public Role role;
        public boolean enabled=true;
        public int authVersion=0;
        // Null marks a pre-verification account; only new public registrations are pending.
        public Boolean emailVerified;
        public Instant emailVerifiedAt;
        @Column(unique=true,length=64) public String verificationHash;
        public Instant verificationExpiresAt;
        public Instant verificationSentAt;
        @Column(unique=true,length=64) public String passwordResetHash;
        public Instant passwordResetExpiresAt;
        public Instant passwordResetSentAt;
        public Long universityId;
        public String department="Computer Science";
        public Integer graduationYear=2028;
    }
    @Entity @Table(name="universities")
    public static class University extends Base { public String name; public String city; }
    @Entity(name="StudentProfile") @Table(name="student_profiles",uniqueConstraints=@UniqueConstraint(columnNames="userId"))
    public static class Profile extends Base {
        public Long userId;
        public String headline="";
        @Column(length=4000) public String about="";
        public String city="";
        public Double cgpa;
        public Long academicRevision; // Null is revision 0 for existing profiles.
        @Embedded public Academics academics;
        public Double experienceYears=0.0;
        public String preferredRole="Software Engineer";
        public String githubUsername="";
        public String leetcodeUsername="";
        @Column(length=12000) public String githubSnapshot="";
        public Instant codingSyncedAt;
        public boolean visibleToRecruiters=true;
        @ElementCollection(fetch=FetchType.EAGER) public List<String> skills=new ArrayList<>();
    }
    @Embeddable
    public static class Academics {
        public String tenthInstitution, tenthBoard, tenthScoreType, tenthGrade;
        public Integer tenthYear;
        public Double tenthScore, tenthScale;
        public String secondaryQualification, secondaryInstitution, secondaryBoard, secondaryScoreType, secondaryGrade;
        public Integer secondaryYear;
        public Double secondaryScore, secondaryScale;
        public String collegeInstitution, degree;
        public Integer currentSemester, activeBacklogs, clearedBacklogs, gapYears;
    }
    public enum AcademicDocumentKind { TENTH, SECONDARY, COLLEGE, OTHER }
    public enum AcademicReviewStatus { PENDING, VERIFIED, CHANGES_REQUESTED }
    @Entity(name="AcademicDocument") @Table(name="academic_documents")
    public static class AcademicDocument extends Base {
        public Long userId;
        @Enumerated(EnumType.STRING) public AcademicDocumentKind kind;
        public String filename, storageKey, mediaType;
        public long sizeBytes;
        public Instant removedAt;
    }
    @Entity(name="AcademicSubmission") @Table(name="academic_submissions")
    public static class AcademicSubmission extends Base {
        public Long studentId, universityId;
        public long academicRevision;
        @Column(length=20000) public String snapshotJson;
        @Column(length=1000) public String studentNote;
        @Enumerated(EnumType.STRING) public AcademicReviewStatus status=AcademicReviewStatus.PENDING;
        @ElementCollection(fetch=FetchType.EAGER) public List<Long> documentIds=new ArrayList<>();
        public Long reviewerId;
        public Instant reviewedAt;
        @Column(length=2000) public String reviewNote;
    }
    @Entity @Table(name="student_projects")
    public static class Project extends Base {
        public Long userId; public String title; public String stack;
        @Column(length=4000) public String description;
        @Column(length=2048) public String repositoryUrl;
        @Column(length=2048) public String liveUrl;
    }
    @Entity @Table(name="student_education")
    public static class Education extends Base {
        public Long userId; public String institution; public String qualification; public Integer passingYear; public String grade;
    }
    @Entity @Table(name="student_experience")
    public static class Experience extends Base {
        public Long userId; public String company; public String title; public String duration;
        @Column(length=4000) public String description;
    }
    @Entity @Table(name="companies",uniqueConstraints=@UniqueConstraint(columnNames="userId"))
    public static class Company extends Base {
        public Long userId; public String name="";
        @Column(length=2048) public String website="";
        public String location="";
        @Column(length=4000) public String description="";
        public boolean verified=false;
    }
    @Entity @Table(name="jobs")
    public static class Job extends Base {
        public Long recruiterId; public String title; public String companyName; public String location;
        @Column(length=12000) public String description;
        public String employmentType="INTERNSHIP"; public String workMode="HYBRID";
        public Integer salaryMin=0; public Integer salaryMax=0; public Integer openings=1;
        public Double minCgpa=0.0; public Double minExperience=0.0;
        public Double minTenthPercentage, minSecondaryPercentage;
        public Integer maxActiveBacklogs;
        @ElementCollection(fetch=FetchType.EAGER) public Set<String> allowedBranches=new LinkedHashSet<>();
        @ElementCollection(fetch=FetchType.EAGER) public Set<Integer> allowedGraduationYears=new LinkedHashSet<>();
        public String status="ACTIVE"; public LocalDate deadline;
        public Long universityId;
        @ElementCollection(fetch=FetchType.EAGER) public List<String> requiredSkills=new ArrayList<>();
    }
    @Entity @Table(name="applications",uniqueConstraints=@UniqueConstraint(columnNames={"studentId","jobId"}))
    public static class Application extends Base {
        public Long studentId; public Long jobId; public Long resumeId;
        @Enumerated(EnumType.STRING) public Stage status=Stage.APPLIED;
        @Column(length=4000) public String coverLetter="";
        public double matchScore;
    }
    public enum DriveStatus { DRAFT, PUBLISHED, COMPLETED, CANCELLED }
    public enum Attendance { PENDING, PRESENT, ABSENT }
    public enum RoundOutcome { PENDING, PASSED, NOT_SELECTED }
    @Entity @Table(name="campus_drives")
    public static class CampusDrive extends Base {
        public Long universityId, jobId, creatorId;
        public String title, venue;
        @Column(length=4000) public String instructions;
        public Instant registrationOpensAt, registrationClosesAt;
        public boolean requireVerifiedAcademics;
        @Enumerated(EnumType.STRING) public DriveStatus status=DriveStatus.DRAFT;
    }
    @Entity @Table(name="drive_rounds",uniqueConstraints=@UniqueConstraint(columnNames={"driveId","position"}))
    public static class DriveRound extends Base {
        public Long driveId;
        public int position;
        public String name, venue;
        public Instant scheduledAt;
        @Column(length=2000) public String instructions;
    }
    @Entity @Table(name="drive_registrations",uniqueConstraints=@UniqueConstraint(columnNames={"driveId","studentId"}))
    public static class DriveRegistration extends Base {
        public Long driveId, studentId, applicationId;
        public String status="REGISTERED";
    }
    @Entity @Table(name="drive_results",uniqueConstraints=@UniqueConstraint(columnNames={"registrationId","roundId"}))
    public static class DriveResult extends Base {
        public Long registrationId, roundId, updatedBy;
        @Enumerated(EnumType.STRING) public Attendance attendance=Attendance.PENDING;
        @Enumerated(EnumType.STRING) public RoundOutcome outcome=RoundOutcome.PENDING;
        @Column(length=2000) public String note="";
        public Instant updatedAt;
    }
    @Entity @Table(name="application_events")
    public static class ApplicationEvent extends Base {
        public Long applicationId; public Long actorId; public String status;
        @Column(length=4000) public String note;
    }
    @Entity @Table(name="saved_jobs",uniqueConstraints=@UniqueConstraint(columnNames={"studentId","jobId"}))
    public static class SavedJob extends Base { public Long studentId; public Long jobId; }
    @Entity @Table(name="resume_versions")
    public static class Resume extends Base {
        public Long userId; public String filename; public String storageKey; public String mediaType;
        public long sizeBytes;
        @Column(length=60000) public String extractedText;
        @ElementCollection(fetch=FetchType.EAGER) public List<String> detectedSkills=new ArrayList<>();
    }
    @Entity @Table(name="notifications")
    public static class Notice extends Base {
        public Long userId; public String title;
        @Column(length=4000) public String message;
        public String link; public boolean read=false;
    }
    @Entity @Table(name="interviews")
    public static class Interview extends Base {
        public Long applicationId; public Long recruiterId; public Long studentId;
        public Instant scheduledAt; public int durationMinutes=30;
        @Column(length=2048) public String meetingUrl;
        @Column(length=2000) public String instructions;
        public String status="SCHEDULED";
    }
    @Entity @Table(name="mock_sessions")
    public static class MockSession extends Base {
        public Long userId; public String targetRole; public String skill; public String engine;
        @Column(length=30000) public String questionsJson;
        @Column(length=200000) public String answersJson;
        @Column(length=30000) public String feedbackJson;
        public String status="IN_PROGRESS"; public Double score;
    }
    @Entity @Table(name="audit_events")
    public static class Audit extends Base { public Long actorId; public String action; public String target; public String detail; }
}

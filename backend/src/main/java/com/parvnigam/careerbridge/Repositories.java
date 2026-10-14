package com.parvnigam.careerbridge;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public final class Repositories {
    public interface Accounts extends JpaRepository<Model.Account,Long> { Optional<Model.Account> findByEmail(String email);
        @org.springframework.data.jpa.repository.Query("select a.id from Account a where a.verificationHash = :hash")
        Optional<Long> verificationAccountId(@org.springframework.data.repository.query.Param("hash") String hash);
        @org.springframework.data.jpa.repository.Query("select a.id from Account a where a.passwordResetHash = :hash")
        Optional<Long> passwordResetAccountId(@org.springframework.data.repository.query.Param("hash") String hash);
        @org.springframework.data.jpa.repository.Query("select a.id from Account a where a.email = :email")
        Optional<Long> emailAccountId(@org.springframework.data.repository.query.Param("email") String email);
        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
        Optional<Model.Account> lockAccount(@org.springframework.data.repository.query.Param("id") Long id); }
    public interface Universities extends JpaRepository<Model.University,Long> {}
    public interface Profiles extends JpaRepository<Model.Profile,Long> { Optional<Model.Profile> findByUserId(Long id);
        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select p from StudentProfile p where p.userId = :id")
        Optional<Model.Profile> lockByUserId(@org.springframework.data.repository.query.Param("id") Long id); }
    public interface AcademicDocuments extends JpaRepository<Model.AcademicDocument,Long> { List<Model.AcademicDocument> findByUserIdOrderByIdDesc(Long id); }
    public interface AcademicSubmissions extends JpaRepository<Model.AcademicSubmission,Long> {
        List<Model.AcademicSubmission> findByStudentIdOrderByIdDesc(Long id);
        List<Model.AcademicSubmission> findByUniversityIdOrderByIdDesc(Long id);
        List<Model.AcademicSubmission> findAllByOrderByIdDesc();
        @org.springframework.data.jpa.repository.Query("select s.studentId from AcademicSubmission s where s.id = :id")
        Optional<Long> studentId(@org.springframework.data.repository.query.Param("id") Long id);
        @org.springframework.data.jpa.repository.Query("select count(s) from AcademicSubmission s join s.documentIds d where d = :id")
        long documentReferences(@org.springframework.data.repository.query.Param("id") Long id);
        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select s from AcademicSubmission s where s.id = :id")
        Optional<Model.AcademicSubmission> lockSubmission(@org.springframework.data.repository.query.Param("id") Long id);
    }
    public interface Projects extends JpaRepository<Model.Project,Long> { List<Model.Project> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Educations extends JpaRepository<Model.Education,Long> { List<Model.Education> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Experiences extends JpaRepository<Model.Experience,Long> { List<Model.Experience> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Companies extends JpaRepository<Model.Company,Long> { Optional<Model.Company> findByUserId(Long id); }
    public interface Jobs extends JpaRepository<Model.Job,Long> { List<Model.Job> findByRecruiterIdOrderByCreatedAtDesc(Long id); }
    public interface Applications extends JpaRepository<Model.Application,Long> {
        List<Model.Application> findByStudentIdOrderByCreatedAtDesc(Long id);
        List<Model.Application> findByJobIdOrderByCreatedAtDesc(Long id);
        Optional<Model.Application> findByStudentIdAndJobId(Long sid,Long jid);
    }
    public interface CampusDrives extends JpaRepository<Model.CampusDrive,Long> {
        List<Model.CampusDrive> findByUniversityIdOrderByIdDesc(Long id);
        List<Model.CampusDrive> findByJobIdInOrderByIdDesc(Collection<Long> ids);
        List<Model.CampusDrive> findAllByOrderByIdDesc();
    }
    public interface DriveRounds extends JpaRepository<Model.DriveRound,Long> { List<Model.DriveRound> findByDriveIdOrderByPositionAsc(Long id); }
    public interface DriveRegistrations extends JpaRepository<Model.DriveRegistration,Long> {
        List<Model.DriveRegistration> findByDriveIdOrderByIdAsc(Long id);
        Optional<Model.DriveRegistration> findByDriveIdAndStudentId(Long driveId,Long studentId);
    }
    public interface DriveResults extends JpaRepository<Model.DriveResult,Long> { List<Model.DriveResult> findByRegistrationIdOrderByIdAsc(Long id); }
    public interface ApplicationEvents extends JpaRepository<Model.ApplicationEvent,Long> { List<Model.ApplicationEvent> findByApplicationIdOrderByCreatedAtAsc(Long id); }
    public interface SavedJobs extends JpaRepository<Model.SavedJob,Long> { List<Model.SavedJob> findByStudentId(Long id); Optional<Model.SavedJob> findByStudentIdAndJobId(Long sid,Long jid); }
    public interface Resumes extends JpaRepository<Model.Resume,Long> { List<Model.Resume> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface ResumeReports extends JpaRepository<Model.ResumeReport,Long> {
        List<Model.ResumeReport> findByUserIdOrderByCreatedAtDescIdDesc(Long id,org.springframework.data.domain.Pageable page);
    }
    public interface Notices extends JpaRepository<Model.Notice,Long> { List<Model.Notice> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Interviews extends JpaRepository<Model.Interview,Long> { List<Model.Interview> findByStudentIdOrderByScheduledAtAsc(Long id); List<Model.Interview> findByRecruiterIdOrderByScheduledAtAsc(Long id); }
    public interface Mocks extends JpaRepository<Model.MockSession,Long> { List<Model.MockSession> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Audits extends JpaRepository<Model.Audit,Long> {}
}

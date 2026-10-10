package com.parvnigam.careerbridge;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public final class Repositories {
    public interface Accounts extends JpaRepository<Model.Account,Long> { Optional<Model.Account> findByEmail(String email);
        @org.springframework.data.jpa.repository.Query("select a.id from Account a where a.verificationHash = :hash")
        Optional<Long> verificationAccountId(@org.springframework.data.repository.query.Param("hash") String hash);
        @org.springframework.data.jpa.repository.Query("select a.id from Account a where a.email = :email")
        Optional<Long> emailAccountId(@org.springframework.data.repository.query.Param("email") String email);
        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @org.springframework.data.jpa.repository.Query("select a from Account a where a.id = :id")
        Optional<Model.Account> lockAccount(@org.springframework.data.repository.query.Param("id") Long id); }
    public interface Universities extends JpaRepository<Model.University,Long> {}
    public interface Profiles extends JpaRepository<Model.Profile,Long> { Optional<Model.Profile> findByUserId(Long id); }
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
    public interface ApplicationEvents extends JpaRepository<Model.ApplicationEvent,Long> { List<Model.ApplicationEvent> findByApplicationIdOrderByCreatedAtAsc(Long id); }
    public interface SavedJobs extends JpaRepository<Model.SavedJob,Long> { List<Model.SavedJob> findByStudentId(Long id); Optional<Model.SavedJob> findByStudentIdAndJobId(Long sid,Long jid); }
    public interface Resumes extends JpaRepository<Model.Resume,Long> { List<Model.Resume> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Notices extends JpaRepository<Model.Notice,Long> { List<Model.Notice> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Interviews extends JpaRepository<Model.Interview,Long> { List<Model.Interview> findByStudentIdOrderByScheduledAtAsc(Long id); List<Model.Interview> findByRecruiterIdOrderByScheduledAtAsc(Long id); }
    public interface Mocks extends JpaRepository<Model.MockSession,Long> { List<Model.MockSession> findByUserIdOrderByCreatedAtDesc(Long id); }
    public interface Audits extends JpaRepository<Model.Audit,Long> {}
}

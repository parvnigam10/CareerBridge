package com.parvnigam.careerbridge;

import java.time.*;
import java.util.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoSeed implements CommandLineRunner {
    final Store r;final PasswordEncoder passwords;final boolean enabled;final String password;
    public DemoSeed(Store r,PasswordEncoder passwords,@Value("${app.seed-demo}")boolean enabled,@Value("${app.demo-password}")String password){this.r=r;this.passwords=passwords;this.enabled=enabled;this.password=password;}
    @Override @Transactional public void run(String... args){if(!enabled||r.accounts().count()>0)return;
        Model.University u=new Model.University();u.name="SRM IST · Delhi NCR";u.city="Delhi NCR";r.universities().save(u);
        Model.University other=new Model.University();other.name="Northfield Institute (Demo)";other.city="Pune";r.universities().save(other);
        Model.Account student=account("student@careerbridge.dev","Parv Nigam",Model.Role.STUDENT,u.id);
        Model.Account recruiter=account("recruiter@careerbridge.dev","Ananya Rao",Model.Role.RECRUITER,null);
        Model.Account officer=account("placement@careerbridge.dev","Dr. Meera Shah",Model.Role.PLACEMENT_OFFICER,u.id);
        Model.Account admin=account("admin@careerbridge.dev","Platform Administrator",Model.Role.ADMIN,null);
        Model.Account second=account("aarav@careerbridge.dev","Aarav Sharma",Model.Role.STUDENT,u.id);
        Model.Account third=account("riya@careerbridge.dev","Riya Kapoor",Model.Role.STUDENT,u.id);
        Model.Account outsider=account("outside@careerbridge.dev","Dev Mehta",Model.Role.STUDENT,other.id);
        Model.Account otherRecruiter=account("other-recruiter@careerbridge.dev","Kabir Sen",Model.Role.RECRUITER,null);
        profile(student,List.of("Java","SQL","Python","DSA","Git"),9.23,"Aspiring Java backend developer","Delhi NCR");
        profile(second,List.of("React","JavaScript","CSS","Git"),8.4,"Frontend developer building accessible experiences","Delhi NCR");
        profile(third,List.of("Python","SQL","Machine Learning","Git"),8.9,"Data science student and problem solver","Delhi NCR");
        profile(outsider,List.of("Java","Docker","Spring Boot"),8.1,"Backend engineer","Pune");
        company(recruiter,"CareerBridge Labs","https://example.com","Delhi NCR","A fictional product studio building useful software. Demo openings for students and graduates.");
        company(otherRecruiter,"Northstar Analytics","https://example.com","Bengaluru","A fictional analytics company for demonstrating recruiter isolation.");
        Model.Project project=new Model.Project();project.userId=student.id;project.title="Football Match Outcome Prediction";project.stack="Python, Pandas, scikit-learn";project.description="A pre-match classification project comparing feature engineering and machine learning models.";project.repositoryUrl="";project.liveUrl="";r.projects().save(project);
        Model.Education education=new Model.Education();education.userId=student.id;education.institution=u.name;education.qualification="B.Tech Computer Science";education.passingYear=2028;education.grade="9.23 CGPA";r.educations().save(education);
        Model.Job java=job(recruiter,"Java Backend Intern","Delhi NCR","INTERNSHIP","HYBRID",List.of("Java","Spring Boot","SQL","Git"),15000,25000,7.0,u.id);
        Model.Job react=job(recruiter,"Frontend Developer Intern","Remote","INTERNSHIP","REMOTE",List.of("React","JavaScript","CSS","Git"),18000,28000,0.0,null);
        Model.Job junior=job(recruiter,"Graduate Software Engineer","Bengaluru","FULL_TIME","ONSITE",List.of("Java","DSA","SQL","Git"),600000,900000,7.5,null);
        Model.Job data=job(otherRecruiter,"Data Science Intern","Remote","INTERNSHIP","REMOTE",List.of("Python","SQL","Machine Learning"),20000,30000,7.0,null);
        job(recruiter,"Platform Engineering Intern","Delhi NCR","INTERNSHIP","HYBRID",List.of("Linux","Docker","Git","AWS"),20000,32000,7.5,u.id);
        job(recruiter,"Full Stack Developer","Remote","FULL_TIME","REMOTE",List.of("Java","React","PostgreSQL","Docker"),700000,1100000,0.0,null);
        job(otherRecruiter,"Business Analytics Intern","Bengaluru","INTERNSHIP","HYBRID",List.of("SQL","Python","Excel","Communication"),16000,22000,6.5,null);
        app(student,java,Model.Stage.SHORTLISTED,82);app(second,react,Model.Stage.INTERVIEW,94);app(third,data,Model.Stage.HIRED,96);
        Model.SavedJob saved=new Model.SavedJob();saved.studentId=student.id;saved.jobId=junior.id;r.saves().save(saved);
        Model.Notice n=new Model.Notice();n.userId=student.id;n.title="Welcome to CareerBridge";n.message="Your next opportunity starts with a complete profile. Explore your matches and preparation path.";n.link="/profile";r.notices().save(n);
        Model.Audit audit=new Model.Audit();audit.actorId=admin.id;audit.action="DEMO_DATA_CREATED";audit.target="platform";audit.detail="Seeded fictional demo accounts and opportunities";r.audits().save(audit);
    }
    Model.Account account(String email,String name,Model.Role role,Long campus){Model.Account a=new Model.Account();a.email=email;a.name=name;a.role=role;a.universityId=campus;a.passwordHash=passwords.encode(password);return r.accounts().save(a);}
    void profile(Model.Account a,List<String>skills,double cgpa,String headline,String city){Model.Profile p=new Model.Profile();p.userId=a.id;p.skills=new ArrayList<>(skills);p.cgpa=cgpa;p.headline=headline;p.city=city;p.about="I enjoy building practical projects, solving problems and collaborating with teams.";r.profiles().save(p);}
    void company(Model.Account a,String name,String website,String location,String description){Model.Company c=new Model.Company();c.userId=a.id;c.name=name;c.website=website;c.location=location;c.description=description;c.verified=true;r.companies().save(c);}
    Model.Job job(Model.Account a,String title,String location,String type,String mode,List<String>skills,int min,int max,double cgpa,Long campus){Model.Job j=new Model.Job();j.recruiterId=a.id;j.title=title;j.companyName=r.companies().findByUserId(a.id).orElseThrow().name;j.description="Join our demo team as a "+title+". Build practical features, collaborate with mentors, write reliable tests and document your work. We value curiosity, clear communication and a strong foundation in "+String.join(", ",skills)+".";j.location=location;j.employmentType=type;j.workMode=mode;j.requiredSkills=new ArrayList<>(skills);j.salaryMin=min;j.salaryMax=max;j.minCgpa=cgpa;j.universityId=campus;j.deadline=LocalDate.now().plusDays(45);j.openings=3;return r.jobs().save(j);}
    void app(Model.Account student,Model.Job job,Model.Stage stage,double score){Model.Application a=new Model.Application();a.studentId=student.id;a.jobId=job.id;a.status=stage;a.matchScore=score;a.coverLetter="I am excited to contribute and learn through this opportunity.";r.applications().save(a);Model.ApplicationEvent e=new Model.ApplicationEvent();e.applicationId=a.id;e.actorId=student.id;e.status=stage.name();e.note="Demo application";r.events().save(e);}
}

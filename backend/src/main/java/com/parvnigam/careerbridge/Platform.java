package com.parvnigam.careerbridge;

import java.time.*;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class Platform {
    final Store r;
    final NotificationService notifications;
    public Platform(Store store,NotificationService notices) {r=store;notifications=notices;}
    static ResponseStatusException fail(int code,String message) {return new ResponseStatusException(HttpStatus.valueOf(code),message);}
    static String clean(String s) {return s==null?"":s.trim();}
    static List<String> skills(List<String> values) {return values==null?List.of():values.stream().map(Platform::clean).filter(x->!x.isEmpty()).distinct().toList();}
    static Map<String,Object> map(Object... entries) {
        Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)m.put(entries[i].toString(),entries[i+1]);return m;
    }
    Model.Account actor(Jwt jwt) {
        if(jwt==null) throw fail(401,"Please sign in");
        Model.Account a=r.accounts().findById(Long.valueOf(jwt.getSubject())).orElseThrow(()->fail(401,"Account not found"));
        Number session=jwt.getClaim("sessionVersion");
        if(!a.enabled || session==null || session.intValue()!=a.authVersion) throw fail(401,"Your session is no longer active");
        return a;
    }
    Map<String,Object> user(Model.Account a) {return map("id",a.id,"name",a.name,"email",a.email,"role",a.role,"enabled",a.enabled,"universityId",a.universityId,"department",a.department,"graduationYear",a.graduationYear);}
    Model.Profile profile(Long uid) {return r.profiles().findByUserId(uid).orElseThrow(()->fail(404,"Student profile not found"));}
    Model.Job job(Long id) {return r.jobs().findById(id).orElseThrow(()->fail(404,"Job not found"));}
    Model.Job ownJob(Model.Account a,Long id) {Model.Job j=job(id);if(!j.recruiterId.equals(a.id))throw fail(403,"This opening belongs to another recruiter");return j;}
    boolean canSee(Model.Account a,Model.Job j) {return j.universityId==null || a.role==Model.Role.ADMIN || a.role==Model.Role.RECRUITER || Objects.equals(a.universityId,j.universityId);}
    boolean active(Model.Job j) {return j.status.equals("ACTIVE") && (j.deadline==null || !j.deadline.isBefore(LocalDate.now()));}
    void audit(Model.Account a,String action,String target,String detail) {Model.Audit x=new Model.Audit();x.actorId=a.id;x.action=action;x.target=target;x.detail=detail;r.audits().save(x);}
    void event(Model.Application a,Model.Account actor,String note) {Model.ApplicationEvent e=new Model.ApplicationEvent();e.applicationId=a.id;e.actorId=actor.id;e.status=a.status.name();e.note=clean(note);r.events().save(e);}
    Map<String,Object> match(Model.Profile p,Model.Job j) {
        Set<String> have=p.skills.stream().map(x->x.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> matched=j.requiredSkills.stream().filter(x->have.contains(x.toLowerCase(Locale.ROOT))).toList();
        List<String> missing=j.requiredSkills.stream().filter(x->!have.contains(x.toLowerCase(Locale.ROOT))).toList();
        double skill=j.requiredSkills.isEmpty()?0:(double)matched.size()/j.requiredSkills.size();
        double academics=p.cgpa>=j.minCgpa?1:Math.max(0,p.cgpa/Math.max(j.minCgpa,1));
        double experience=p.experienceYears>=j.minExperience?1:p.experienceYears/Math.max(j.minExperience,1);
        double location=j.workMode.equals("REMOTE")?1:clean(p.city).equalsIgnoreCase(clean(j.location))?1:0.4;
        boolean eligible=p.cgpa>=j.minCgpa && p.experienceYears>=j.minExperience;
        double score=Math.round((skill*.7+academics*.15+experience*.1+location*.05)*1000)/10.0;
        return map("score",score,"matchedSkills",matched,"missingSkills",missing,"eligible",eligible,
            "breakdown",map("skills",Math.round(skill*100),"academics",Math.round(academics*100),"experience",Math.round(experience*100),"location",Math.round(location*100)),
            "method","Weighted profile compatibility; not a hiring probability");
    }
    Map<String,Object> jobView(Model.Job j,Model.Account a) {
        Map<String,Object> v=map("id",j.id,"title",j.title,"companyName",j.companyName,"description",j.description,"location",j.location,"employmentType",j.employmentType,"workMode",j.workMode,
            "salaryMin",j.salaryMin,"salaryMax",j.salaryMax,"openings",j.openings,"minCgpa",j.minCgpa,"minExperience",j.minExperience,"deadline",j.deadline,"status",active(j)?j.status:j.status.equals("ACTIVE")?"EXPIRED":j.status,
            "universityId",j.universityId,"requiredSkills",j.requiredSkills,"createdAt",j.createdAt);
        if(a!=null && a.role==Model.Role.STUDENT) {v.put("match",match(profile(a.id),j));v.put("saved",r.saves().findByStudentIdAndJobId(a.id,j.id).isPresent());v.put("applied",r.applications().findByStudentIdAndJobId(a.id,j.id).isPresent());}
        if(a!=null && a.role==Model.Role.RECRUITER && a.id.equals(j.recruiterId))v.put("applicantCount",r.applications().findByJobIdOrderByCreatedAtDesc(j.id).size());
        return v;
    }
    List<Map<String,Object>> jobs(Model.Account a,String q,String type,String mode,boolean onlyMine) {
        return r.jobs().findAll().stream().filter(j->onlyMine?a!=null&&j.recruiterId.equals(a.id):active(j)&&(a==null?j.universityId==null:canSee(a,j)))
            .filter(j->clean(q).isEmpty()||(j.title+" "+j.companyName+" "+j.location+" "+String.join(" ",j.requiredSkills)).toLowerCase().contains(q.toLowerCase()))
            .filter(j->clean(type).isEmpty()||j.employmentType.equals(type)).filter(j->clean(mode).isEmpty()||j.workMode.equals(mode))
            .sorted(Comparator.comparing((Model.Job j)->j.createdAt).reversed()).map(j->jobView(j,a)).toList();
    }
    Map<String,Object> fullProfile(Model.Account a) {Model.Profile p=profile(a.id);return map("user",user(a),"profile",p,"projects",r.projects().findByUserIdOrderByCreatedAtDesc(a.id),"education",r.educations().findByUserIdOrderByCreatedAtDesc(a.id),"experience",r.experiences().findByUserIdOrderByCreatedAtDesc(a.id),"completion",completion(p,a.id));}
    int completion(Model.Profile p,Long id) {return (int)Stream.of(!p.headline.isBlank(),!p.about.isBlank(),!p.city.isBlank(),!p.skills.isEmpty(),p.cgpa>0,!r.projects().findByUserIdOrderByCreatedAtDesc(id).isEmpty(),!r.educations().findByUserIdOrderByCreatedAtDesc(id).isEmpty(),!r.resumes().findByUserIdOrderByCreatedAtDesc(id).isEmpty()).filter(Boolean::booleanValue).count()*100/8;}
    void updateProfile(Model.Account a,Inputs.Profile d) {
        Model.Profile p=profile(a.id);p.headline=clean(d.headline());p.about=clean(d.about());p.city=clean(d.city());p.cgpa=d.cgpa()==null?0:d.cgpa();p.experienceYears=d.experienceYears()==null?0:d.experienceYears();
        p.preferredRole=clean(d.preferredRole());p.githubUsername=clean(d.githubUsername());p.leetcodeUsername=clean(d.leetcodeUsername());p.visibleToRecruiters=d.visibleToRecruiters();p.skills=new ArrayList<>(skills(d.skills()));r.profiles().save(p);
    }
    Model.Job saveJob(Model.Account a,Long id,Inputs.Job d) {
        Model.Company c=r.companies().findByUserId(a.id).orElseThrow();if(c.name.isBlank())throw fail(400,"Complete your company profile first");
        if(!Set.of("INTERNSHIP","FULL_TIME","PART_TIME","CONTRACT").contains(d.employmentType())||!Set.of("REMOTE","HYBRID","ONSITE").contains(d.workMode()))throw fail(400,"Invalid employment type or work mode");
        if(d.salaryMin()!=null && d.salaryMax()!=null && d.salaryMax()<d.salaryMin())throw fail(400,"Maximum salary must be at least the minimum");
        if(d.universityId()!=null && !r.universities().existsById(d.universityId()))throw fail(400,"University not found");
        Model.Job j=id==null?new Model.Job():ownJob(a,id);j.recruiterId=a.id;j.companyName=c.name;j.title=d.title().trim();j.description=d.description().trim();j.location=d.location().trim();j.employmentType=d.employmentType();j.workMode=d.workMode();
        j.salaryMin=d.salaryMin()==null?0:d.salaryMin();j.salaryMax=d.salaryMax()==null?0:d.salaryMax();j.openings=d.openings()==null?1:d.openings();j.minCgpa=d.minCgpa()==null?0:d.minCgpa();j.minExperience=d.minExperience()==null?0:d.minExperience();j.deadline=d.deadline();j.universityId=d.universityId();j.requiredSkills=new ArrayList<>(skills(d.requiredSkills()));
        r.jobs().save(j);audit(a,id==null?"JOB_CREATED":"JOB_UPDATED",j.id.toString(),j.title);return j;
    }
    Map<String,Object> applicationView(Model.Application a,Model.Account viewer) {
        Model.Job j=job(a.jobId);Model.Account student=r.accounts().findById(a.studentId).orElseThrow();
        Map<String,Object> v=map("id",a.id,"job",jobView(j,null),"student",user(student),"status",a.status,"coverLetter",a.coverLetter,"createdAt",a.createdAt,"matchScore",a.matchScore,"resumeId",a.resumeId,"history",r.events().findByApplicationIdOrderByCreatedAtAsc(a.id));
        if(viewer.role!=Model.Role.STUDENT)v.put("profile",fullProfile(student));return v;
    }
    Model.Application apply(Model.Account a,Inputs.Apply d) {
        Model.Job j=job(d.jobId());if(!active(j))throw fail(400,"This opening is closed");if(!canSee(a,j))throw fail(403,"This opening is for another university");
        Model.Profile p=profile(a.id);Map<String,Object> m=match(p,j);if(!(boolean)m.get("eligible"))throw fail(400,"You do not meet the CGPA or experience eligibility requirements");
        if(r.applications().findByStudentIdAndJobId(a.id,j.id).isPresent())throw fail(409,"You have already applied for this opening");
        Model.Application x=new Model.Application();x.studentId=a.id;x.jobId=j.id;x.coverLetter=clean(d.coverLetter());x.matchScore=(double)m.get("score");
        x.resumeId=r.resumes().findByUserIdOrderByCreatedAtDesc(a.id).stream().findFirst().map(z->z.id).orElse(null);r.applications().save(x);event(x,a,"Application submitted");
        notifications.send(j.recruiterId,"New applicant",a.name+" applied for "+j.title,"/pipeline");notifications.send(a.id,"Application received","You applied for "+j.title,"/applications");audit(a,"APPLIED",x.id.toString(),j.title);return x;
    }
    void stage(Model.Account recruiter,Long id,Inputs.Stage d) {
        Model.Application a=r.applications().findById(id).orElseThrow(()->fail(404,"Application not found"));ownJob(recruiter,a.jobId);
        Map<Model.Stage,Set<Model.Stage>> transitions=Map.of(Model.Stage.APPLIED,Set.of(Model.Stage.SHORTLISTED,Model.Stage.REJECTED),Model.Stage.SHORTLISTED,Set.of(Model.Stage.INTERVIEW,Model.Stage.REJECTED),Model.Stage.INTERVIEW,Set.of(Model.Stage.OFFERED,Model.Stage.REJECTED),Model.Stage.OFFERED,Set.of(Model.Stage.HIRED,Model.Stage.REJECTED));
        if(!transitions.getOrDefault(a.status,Set.of()).contains(d.status()))throw fail(400,"This application stage transition is not allowed");
        a.status=d.status();r.applications().save(a);event(a,recruiter,d.note());notifications.send(a.studentId,"Application update",job(a.jobId).title+": "+a.status,"/applications");audit(recruiter,"STAGE_CHANGED",a.id.toString(),a.status.name());
    }
    void withdraw(Model.Account student,Long id) {Model.Application a=r.applications().findById(id).orElseThrow(()->fail(404,"Application not found"));if(!a.studentId.equals(student.id))throw fail(403,"This is another student's application");if(!Set.of(Model.Stage.APPLIED,Model.Stage.SHORTLISTED,Model.Stage.INTERVIEW).contains(a.status))throw fail(400,"This application cannot be withdrawn");a.status=Model.Stage.WITHDRAWN;r.applications().save(a);event(a,student,"Withdrawn by student");}
    List<Map<String,Object>> recruiterApps(Model.Account recruiter) {return r.jobs().findByRecruiterIdOrderByCreatedAtDesc(recruiter.id).stream().flatMap(j->r.applications().findByJobIdOrderByCreatedAtDesc(j.id).stream()).sorted(Comparator.comparing((Model.Application a)->a.createdAt).reversed()).map(a->applicationView(a,recruiter)).toList();}
    List<Map<String,Object>> candidates(Model.Account recruiter,String q,Long jobId) {
        Model.Job j=jobId==null?null:ownJob(recruiter,jobId);
        return r.accounts().findAll().stream().filter(a->a.role==Model.Role.STUDENT&&a.enabled).filter(a->j==null||canSee(a,j)).filter(a->profile(a.id).visibleToRecruiters)
            .filter(a->(a.name+" "+String.join(" ",profile(a.id).skills)).toLowerCase().contains(clean(q).toLowerCase()))
            .map(a->{Map<String,Object> v=map("user",map("id",a.id,"name",a.name,"department",a.department,"graduationYear",a.graduationYear),"profile",profile(a.id),"projects",r.projects().findByUserIdOrderByCreatedAtDesc(a.id));if(j!=null)v.put("match",match(profile(a.id),j));return v;}).toList();
    }
    List<Model.Account> scopedStudents(Model.Account actor,Integer year,String department) {return r.accounts().findAll().stream().filter(a->a.role==Model.Role.STUDENT&&a.enabled)
        .filter(a->actor.role==Model.Role.ADMIN||Objects.equals(actor.universityId,a.universityId)).filter(a->year==null||Objects.equals(year,a.graduationYear))
        .filter(a->clean(department).isEmpty()||clean(a.department).equalsIgnoreCase(department)).toList();}
    Map<String,Object> placement(Model.Account actor,Integer year,String department) {
        List<Model.Account> students=scopedStudents(actor,year,department);Set<Long> ids=students.stream().map(a->a.id).collect(Collectors.toSet());
        List<Model.Application> apps=r.applications().findAll().stream().filter(a->ids.contains(a.studentId)).toList();
        Set<Long> placed=apps.stream().filter(a->a.status==Model.Stage.HIRED).map(a->a.studentId).collect(Collectors.toSet());
        Map<String,Long> stages=apps.stream().collect(Collectors.groupingBy(a->a.status.name(),Collectors.counting()));
        Map<String,Long> skillDemand=r.jobs().findAll().stream().filter(this::active).filter(j->j.universityId==null||Objects.equals(j.universityId,actor.universityId)||actor.role==Model.Role.ADMIN).flatMap(j->j.requiredSkills.stream()).collect(Collectors.groupingBy(x->x,Collectors.counting()));
        List<Map<String,Object>> rows=students.stream().map(a->map("user",user(a),"profile",profile(a.id),"completion",completion(profile(a.id),a.id),"applications",apps.stream().filter(x->x.studentId.equals(a.id)).count(),"placed",placed.contains(a.id))).toList();
        return map("students",rows,"totalStudents",students.size(),"placedStudents",placed.size(),"placementRate",students.isEmpty()?0:Math.round(placed.size()*1000.0/students.size())/10.0,"totalApplications",apps.size(),"stages",stages,"skillDemand",skillDemand);
    }
    List<Map<String,Object>> recommendations(Model.Account student) {return jobs(student,"","","",false).stream().sorted(Comparator.comparingDouble((Map<String,Object> j)->((Number)((Map<?,?>)j.get("match")).get("score")).doubleValue()).reversed()).toList();}
    Map<String,Object> dashboard(Model.Account a) {
        if(a.role==Model.Role.STUDENT) {List<Model.Application> apps=r.applications().findByStudentIdOrderByCreatedAtDesc(a.id);return map("profileCompletion",completion(profile(a.id),a.id),"applications",apps.size(),"interviews",r.interviews().findByStudentIdOrderByScheduledAtAsc(a.id).stream().filter(i->i.status.equals("SCHEDULED")&&i.scheduledAt.isAfter(Instant.now())).count(),"savedJobs",r.saves().findByStudentId(a.id).size(),"recommendations",recommendations(a).stream().limit(4).toList());}
        if(a.role==Model.Role.RECRUITER) {List<Model.Job> jobs=r.jobs().findByRecruiterIdOrderByCreatedAtDesc(a.id);List<Map<String,Object>> apps=recruiterApps(a);return map("activeJobs",jobs.stream().filter(this::active).count(),"applications",apps.size(),"shortlisted",apps.stream().filter(x->x.get("status")==Model.Stage.SHORTLISTED).count(),"hired",apps.stream().filter(x->x.get("status")==Model.Stage.HIRED).count(),"recentApplications",apps.stream().limit(5).toList());}
        return placement(a,null,"");
    }
}

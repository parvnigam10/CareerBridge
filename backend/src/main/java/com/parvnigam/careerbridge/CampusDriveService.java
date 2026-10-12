package com.parvnigam.careerbridge;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class CampusDriveService {
    private final Store r;
    private final Platform p;
    private final EntityManager em;
    private final NotificationService notices;
    CampusDriveService(Store r,Platform p,EntityManager em,NotificationService notices){this.r=r;this.p=p;this.em=em;this.notices=notices;}

    private void staff(Model.Account a,Long university){
        if(a.role==Model.Role.ADMIN)return;
        if(a.role!=Model.Role.PLACEMENT_OFFICER||a.universityId==null||!Objects.equals(a.universityId,university))throw Platform.fail(403,"This drive belongs to another university");
    }
    private Model.CampusDrive drive(Long id,boolean lock){
        var d=lock?em.find(Model.CampusDrive.class,id,LockModeType.PESSIMISTIC_WRITE):r.drives().findById(id).orElse(null);
        if(d==null)throw Platform.fail(404,"Campus drive not found");if(lock)em.refresh(d);return d;
    }
    private Model.Job lockedJob(Long id){var j=em.find(Model.Job.class,id,LockModeType.PESSIMISTIC_WRITE);if(j==null)throw Platform.fail(404,"Opening not found");em.refresh(j);return j;}
    private Model.Account lockedStudent(Model.Account a){
        if(a.role!=Model.Role.STUDENT)throw Platform.fail(403,"Only students can register for a drive");
        var fresh=r.accounts().lockAccount(a.id).orElseThrow();em.refresh(fresh);if(!fresh.enabled||Boolean.FALSE.equals(fresh.emailVerified))throw Platform.fail(401,"Your student session is no longer active");return fresh;
    }
    private Model.Profile lockedProfile(Long id){var x=r.profiles().lockByUserId(id).orElseThrow();em.refresh(x);return x;}
    private List<Model.DriveRound> rounds(Long id){return r.driveRounds().findByDriveIdOrderByPositionAsc(id);}
    private void scope(Model.Account a,Model.CampusDrive d){
        if(a.role==Model.Role.STUDENT){if(!Objects.equals(a.universityId,d.universityId)||d.status==Model.DriveStatus.DRAFT)throw Platform.fail(403,"This drive is not available to your university");}
        else if(a.role==Model.Role.RECRUITER){if(!p.job(d.jobId).recruiterId.equals(a.id)||d.status==Model.DriveStatus.DRAFT)throw Platform.fail(403,"This drive does not use your opening");}
        else staff(a,d.universityId);
    }
    private String window(Model.CampusDrive d){
        if(d.status!=Model.DriveStatus.PUBLISHED)return d.status.name();
        Instant now=Instant.now();return now.isBefore(d.registrationOpensAt)?"UPCOMING":now.isAfter(d.registrationClosesAt)?"CLOSED":"OPEN";
    }
    private boolean terminal(Model.Application a){return Set.of(Model.Stage.REJECTED,Model.Stage.WITHDRAWN,Model.Stage.HIRED).contains(a.status);}
    private String blocked(Model.Account a,Model.CampusDrive d,Model.Job j){
        if(d.status!=Model.DriveStatus.PUBLISHED)return "This drive is "+d.status.name().toLowerCase(Locale.ROOT)+".";
        if(!window(d).equals("OPEN"))return window(d).equals("UPCOMING")?"Registration has not opened yet.":"Registration has closed.";
        if(!p.active(j)||!p.canSee(a,j))return "The linked opening is closed or no longer available to your university.";
        var existing=r.applications().findByStudentIdAndJobId(a.id,j.id);
        if(existing.isPresent()&&terminal(existing.get()))return "Your application is "+existing.get().status.name().toLowerCase(Locale.ROOT)+"; it cannot join this drive.";
        var profile=p.profile(a.id);var eligibility=Eligibility.assess(a,profile,j);
        if(!eligibility.eligible())return eligibility.blockedMessage();
        if(d.requireVerifiedAcademics&&!Boolean.TRUE.equals(p.academicReviewSummary(a,profile).get("verified")))return "This drive requires campus verification of your current academic record.";
        return "";
    }
    private String progress(Model.DriveRegistration registration,List<Model.DriveResult> results){
        var app=r.applications().findById(registration.applicationId).orElseThrow();
        if(registration.status.equals("WITHDRAWN"))return "WITHDRAWN";
        if(app.status==Model.Stage.WITHDRAWN)return "APPLICATION_WITHDRAWN";
        if(app.status==Model.Stage.REJECTED)return "APPLICATION_REJECTED";
        if(results.stream().anyMatch(x->x.outcome==Model.RoundOutcome.NOT_SELECTED))return "NOT_SELECTED";
        if(!results.isEmpty()&&results.stream().allMatch(x->x.outcome==Model.RoundOutcome.PASSED))return "ROUNDS_COMPLETED";
        return results.stream().anyMatch(x->x.attendance!=Model.Attendance.PENDING)?"IN_PROGRESS":"REGISTERED";
    }
    private Map<String,Object> registrationView(Model.DriveRegistration registration){
        var student=r.accounts().findById(registration.studentId).orElseThrow();
        var app=r.applications().findById(registration.applicationId).orElseThrow();var results=r.driveResults().findByRegistrationIdOrderByIdAsc(registration.id);
        return Platform.map("id",registration.id,"version",registration.version,"student",p.user(student),"applicationId",app.id,"applicationStatus",app.status,"registrationStatus",registration.status,"progress",progress(registration,results),"createdAt",registration.createdAt,"academicVerification",p.academicReviewSummary(student,p.profile(student.id)),"results",results);
    }
    private Map<String,Object> view(Model.Account a,Model.CampusDrive d,boolean details){
        var j=p.job(d.jobId);var university=r.universities().findById(d.universityId).orElseThrow();
        var opening=p.canSee(a,j)?p.jobView(j,a):Platform.map("id",j.id,"title","Opening no longer available","companyName","Unavailable opening","status","UNAVAILABLE");
        var v=Platform.map("id",d.id,"version",d.version,"title",d.title,"venue",d.venue,"instructions",d.instructions,"universityId",d.universityId,"universityName",university.name,"status",d.status,"registrationWindow",window(d),"registrationOpensAt",d.registrationOpensAt,"registrationClosesAt",d.registrationClosesAt,"requireVerifiedAcademics",d.requireVerifiedAcademics,"job",opening,"rounds",rounds(d.id));
        if(a.role==Model.Role.STUDENT){
            var mine=r.driveRegistrations().findByDriveIdAndStudentId(d.id,a.id);v.put("registration",mine.map(this::registrationView).orElse(null));v.put("registrationBlockedReason",blocked(a,d,j));
            v.put("canRegister",blocked(a,d,j).isEmpty()&&(mine.isEmpty()||mine.get().status.equals("WITHDRAWN")));
            v.put("canWithdraw",mine.isPresent()&&mine.get().status.equals("REGISTERED")&&d.status==Model.DriveStatus.PUBLISHED&&Instant.now().isBefore(rounds(d.id).get(0).scheduledAt));
        }else{
            var registrations=r.driveRegistrations().findByDriveIdOrderByIdAsc(d.id);v.put("registeredCount",registrations.stream().filter(x->x.status.equals("REGISTERED")).count());
            if(details)v.put("participants",registrations.stream().map(this::registrationView).toList());
        }
        return v;
    }
    public List<Map<String,Object>> list(Model.Account a){
        List<Model.CampusDrive> drives;
        if(a.role==Model.Role.ADMIN)drives=r.drives().findAllByOrderByIdDesc();
        else if(a.role==Model.Role.RECRUITER)drives=r.drives().findByJobIdInOrderByIdDesc(r.jobs().findByRecruiterIdOrderByCreatedAtDesc(a.id).stream().map(j->j.id).toList());
        else {if(a.universityId==null)throw Platform.fail(400,"A registered university is required");drives=r.drives().findByUniversityIdOrderByIdDesc(a.universityId);}
        return drives.stream().filter(d->!(a.role==Model.Role.STUDENT||a.role==Model.Role.RECRUITER)||d.status!=Model.DriveStatus.DRAFT).map(d->view(a,d,false)).toList();
    }
    public Map<String,Object> detail(Model.Account a,Long id){var d=drive(id,false);scope(a,d);return view(a,d,true);}
    private void validate(Model.CampusDrive d,Inputs.Drive input,Model.Job j){
        if(j.universityId!=null&&!j.universityId.equals(d.universityId))throw Platform.fail(400,"Choose a public opening or one for this university");
        if(!p.active(j))throw Platform.fail(400,"Choose an active recruiter opening");
        if(!input.registrationOpensAt().isBefore(input.registrationClosesAt())||!input.registrationClosesAt().isAfter(Instant.now()))throw Platform.fail(400,"Registration must close in the future, after it opens");
        if(j.deadline!=null&&input.registrationClosesAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(j.deadline))throw Platform.fail(400,"Drive registration must close by the opening's application deadline");
        Instant previous=input.registrationClosesAt();Set<String> names=new HashSet<>();
        for(var round:input.rounds()){
            if(!round.scheduledAt().isAfter(previous))throw Platform.fail(400,"Schedule rounds in order, after registration closes");
            if(!names.add(round.name().trim().toLowerCase(Locale.ROOT)))throw Platform.fail(400,"Use a distinct name for each round");previous=round.scheduledAt();
        }
    }
    public Map<String,Object> save(Model.Account a,Long id,Inputs.Drive input){
        var d=id==null?new Model.CampusDrive():drive(id,true);
        Long university=a.role==Model.Role.ADMIN?input.universityId():a.universityId;staff(a,university);
        if(university==null||!r.universities().existsById(university))throw Platform.fail(400,"Choose a university for this drive");
        if(a.role==Model.Role.PLACEMENT_OFFICER&&input.universityId()!=null&&!Objects.equals(input.universityId(),a.universityId))throw Platform.fail(403,"You can create drives only for your own university");
        if(id!=null){staff(a,d.universityId);if(input.version()==null||d.version!=input.version())throw Platform.fail(409,"This drive changed. Refresh it before editing.");if(d.status!=Model.DriveStatus.DRAFT)throw Platform.fail(409,"Only draft drives can be edited. Published schedules are retained for participants.");}
        d.universityId=university;var j=lockedJob(input.jobId());validate(d,input,j);d.jobId=j.id;d.title=input.title().trim();d.venue=input.venue().trim();d.instructions=Platform.clean(input.instructions());d.requireVerifiedAcademics=input.requireVerifiedAcademics();d.registrationOpensAt=input.registrationOpensAt();d.registrationClosesAt=input.registrationClosesAt();if(id==null)d.creatorId=a.id;
        r.drives().saveAndFlush(d);r.driveRounds().deleteAll(rounds(d.id));r.driveRounds().flush();int position=0;
        for(var inputRound:input.rounds()){var x=new Model.DriveRound();x.driveId=d.id;x.position=++position;x.name=inputRound.name().trim();x.scheduledAt=inputRound.scheduledAt();x.venue=inputRound.venue().trim();x.instructions=Platform.clean(inputRound.instructions());r.driveRounds().save(x);}
        p.audit(a,id==null?"DRIVE_CREATED":"DRIVE_EDITED",d.id.toString(),d.title);return view(a,d,true);
    }
    public Map<String,Object> changeStatus(Model.Account a,Long id,Inputs.DriveStatus input){
        var d=drive(id,true);staff(a,d.universityId);if(d.version!=input.version())throw Platform.fail(409,"This drive changed. Refresh before updating it.");
        boolean publish=d.status==Model.DriveStatus.DRAFT&&input.status()==Model.DriveStatus.PUBLISHED;
        boolean cancel=Set.of(Model.DriveStatus.DRAFT,Model.DriveStatus.PUBLISHED).contains(d.status)&&input.status()==Model.DriveStatus.CANCELLED;
        boolean complete=d.status==Model.DriveStatus.PUBLISHED&&input.status()==Model.DriveStatus.COMPLETED;
        if(!publish&&!cancel&&!complete)throw Platform.fail(400,"This drive status transition is not allowed");
        var j=lockedJob(d.jobId);
        if(publish){if(!p.active(j)||j.universityId!=null&&!Objects.equals(j.universityId,d.universityId)||!d.registrationClosesAt.isAfter(Instant.now()))throw Platform.fail(409,"The opening or registration dates changed. Update the draft before publishing.");if(j.deadline!=null&&d.registrationClosesAt.atZone(ZoneOffset.UTC).toLocalDate().isAfter(j.deadline))throw Platform.fail(409,"Registration now exceeds the opening's deadline. Update the draft.");}
        var participants=r.driveRegistrations().findByDriveIdOrderByIdAsc(id);
        if(complete){if(Instant.now().isBefore(rounds(id).get(rounds(id).size()-1).scheduledAt))throw Platform.fail(409,"The final round has not started yet");if(participants.stream().map(this::registrationView).anyMatch(x->Set.of("REGISTERED","IN_PROGRESS").contains(x.get("progress"))))throw Platform.fail(409,"Record round outcomes for all active participants before completing the drive");}
        d.status=input.status();r.drives().saveAndFlush(d);p.audit(a,"DRIVE_STATUS",id.toString(),d.status.name());
        if(publish){p.scopedStudents(a.role==Model.Role.ADMIN?campusActor(d.universityId):a,null,"").forEach(x->notices.send(x.id,"Campus drive registration",d.title+" is available in Campus drives. Check the schedule and eligibility before registering.","/drives"));notices.send(j.recruiterId,"Campus drive published",d.title+" uses your opening: "+j.title,"/drives");}
        else for(var registration:participants)notices.send(registration.studentId,"Campus drive update",d.title+": "+d.status.name().toLowerCase(Locale.ROOT),"/drives");
        return view(a,d,true);
    }
    private Model.Account campusActor(Long university){var a=new Model.Account();a.role=Model.Role.PLACEMENT_OFFICER;a.universityId=university;return a;}
    public Map<String,Object> register(Model.Account actor,Long id){
        var a=lockedStudent(actor);lockedProfile(a.id);var d=drive(id,true);scope(a,d);var j=lockedJob(d.jobId);String reason=blocked(a,d,j);if(!reason.isEmpty())throw Platform.fail(400,reason);
        var previous=r.driveRegistrations().findByDriveIdAndStudentId(id,a.id);if(previous.isPresent()&&previous.get().status.equals("REGISTERED"))throw Platform.fail(409,"You are already registered for this drive");
        var app=r.applications().findByStudentIdAndJobId(a.id,j.id).orElse(null);if(app==null)app=p.apply(a,new Inputs.Apply(j.id,"Registered through campus drive: "+d.title));else {app=em.find(Model.Application.class,app.id,LockModeType.PESSIMISTIC_WRITE);em.refresh(app);if(terminal(app))throw Platform.fail(409,"Your application changed. Refresh the drive before registering.");}
        var registration=previous.orElseGet(Model.DriveRegistration::new);registration.driveId=id;registration.studentId=a.id;registration.applicationId=app.id;registration.status="REGISTERED";r.driveRegistrations().saveAndFlush(registration);
        if(previous.isEmpty())for(var round:rounds(id)){var result=new Model.DriveResult();result.roundId=round.id;result.registrationId=registration.id;r.driveResults().save(result);}
        p.audit(a,"DRIVE_REGISTERED",registration.id.toString(),d.title);notices.send(a.id,"Campus drive registration confirmed",d.title+" is linked to application #"+app.id+". View the schedule in Campus drives.","/drives");return view(a,d,true);
    }
    public Map<String,Object> withdraw(Model.Account actor,Long id){
        var a=lockedStudent(actor);var d=drive(id,true);scope(a,d);if(d.status!=Model.DriveStatus.PUBLISHED||!Instant.now().isBefore(rounds(id).get(0).scheduledAt))throw Platform.fail(409,"Drive withdrawal is available only before its first round begins");
        var registration=r.driveRegistrations().findByDriveIdAndStudentId(id,a.id).orElseThrow(()->Platform.fail(404,"Drive registration not found"));if(registration.status.equals("WITHDRAWN"))return view(a,d,true);
        registration.status="WITHDRAWN";r.driveRegistrations().save(registration);p.audit(a,"DRIVE_WITHDRAWN",registration.id.toString(),d.title);notices.send(a.id,"Campus drive registration withdrawn","You left "+d.title+". Your recruiter application is retained.","/drives");return view(a,d,true);
    }
    public Map<String,Object> result(Model.Account a,Long id,Long resultId,Inputs.DriveResult input){
        var d=drive(id,true);staff(a,d.universityId);if(d.status!=Model.DriveStatus.PUBLISHED)throw Platform.fail(409,"Only published drives accept round updates");
        var result=em.find(Model.DriveResult.class,resultId,LockModeType.PESSIMISTIC_WRITE);if(result==null)throw Platform.fail(404,"Round result not found");em.refresh(result);
        var registration=r.driveRegistrations().findById(result.registrationId).orElseThrow();if(!registration.driveId.equals(id))throw Platform.fail(403,"This result belongs to another drive");
        var student=r.accounts().findById(registration.studentId).orElseThrow();if(!Objects.equals(student.universityId,d.universityId))throw Platform.fail(409,"This student no longer belongs to the drive's university");
        if(result.version!=input.version())throw Platform.fail(409,"This round result changed. Refresh before saving.");
        var app=r.applications().findById(registration.applicationId).orElseThrow();if(!registration.status.equals("REGISTERED")||app.status==Model.Stage.WITHDRAWN||app.status==Model.Stage.REJECTED)throw Platform.fail(409,"This registration or application is withdrawn or rejected");
        var round=r.driveRounds().findById(result.roundId).orElseThrow();if(Instant.now().isBefore(round.scheduledAt))throw Platform.fail(409,"Attendance and results can be recorded after the round begins");
        if(input.attendance()==Model.Attendance.PENDING&&input.outcome()!=Model.RoundOutcome.PENDING||input.attendance()==Model.Attendance.ABSENT&&input.outcome()==Model.RoundOutcome.PASSED)throw Platform.fail(400,"A passed round requires present attendance");
        var all=r.driveResults().findByRegistrationIdOrderByIdAsc(registration.id);
        for(var other:all){int position=r.driveRounds().findById(other.roundId).orElseThrow().position;
            if(position<round.position&&other.outcome!=Model.RoundOutcome.PASSED)throw Platform.fail(409,"The student must pass earlier rounds before this round is updated");
            if(position>round.position&&other.attendance!=Model.Attendance.PENDING&&input.outcome()!=Model.RoundOutcome.PASSED)throw Platform.fail(409,"Later rounds already contain results. Earlier passed outcomes must be retained");
        }
        if(input.outcome()==Model.RoundOutcome.NOT_SELECTED&&Platform.clean(input.note()).isEmpty())throw Platform.fail(400,"Provide feedback for a not-selected outcome");
        result.attendance=input.attendance();result.outcome=input.outcome();result.note=Platform.clean(input.note());result.updatedBy=a.id;result.updatedAt=Instant.now();r.driveResults().saveAndFlush(result);p.audit(a,"DRIVE_ROUND_UPDATED",result.id.toString(),round.name+": "+result.outcome);
        notices.send(student.id,"Campus round update",d.title+" · "+round.name+": "+result.attendance.name().toLowerCase(Locale.ROOT)+", "+result.outcome.name().toLowerCase(Locale.ROOT)+(result.note.isEmpty()?"":". "+result.note),"/drives");return view(a,d,true);
    }
    public String export(Model.Account a,Long id){var d=drive(id,false);scope(a,d);if(a.role==Model.Role.STUDENT)throw Platform.fail(403,"Only campus staff and the opening's recruiter can export participants");
        StringBuilder csv=new StringBuilder("Registration ID,Name,Email,Application ID,Recruiter stage,Campus progress,Round,Attendance,Outcome,Feedback\r\n");
        for(var registration:r.driveRegistrations().findByDriveIdOrderByIdAsc(id)){var v=registrationView(registration);var student=r.accounts().findById(registration.studentId).orElseThrow();for(var result:r.driveResults().findByRegistrationIdOrderByIdAsc(registration.id)){var round=r.driveRounds().findById(result.roundId).orElseThrow();Object[] row={registration.id,student.name,student.email,registration.applicationId,v.get("applicationStatus"),v.get("progress"),round.name,result.attendance,result.outcome,result.note};csv.append(String.join(",",Arrays.stream(row).map(this::csv).toList())).append("\r\n");}}
        return csv.toString();
    }
    private String csv(Object value){String s=Objects.toString(value,"");if(s.matches("(?s)^[\\s]*[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}

package com.parvnigam.careerbridge;

import jakarta.persistence.EntityManager;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@Service @Transactional
public class AcademicReviewService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final long MAX_FILE=5L*1024*1024,MAX_STORAGE=100L*1024*1024;
    private final Store r;private final EntityManager em;private final NotificationService notices;private final Path directory;

    AcademicReviewService(Store r,EntityManager em,NotificationService notices,@Value("${app.upload-dir}") String uploads)throws IOException{
        this.r=r;this.em=em;this.notices=notices;directory=Path.of(uploads).toAbsolutePath().normalize().resolve("academic-documents");Files.createDirectories(directory);
    }
    static long revision(Model.Profile p){return p.academicRevision==null?0:p.academicRevision;}
    static String snapshot(Model.Account a,Model.Profile p){return JSON.writeValueAsString(Platform.map("user",Platform.map("department",a.department,"graduationYear",a.graduationYear),"profile",Platform.map("academics",p.academics,"cgpa",p.cgpa)));}
    static void advanceIfChanged(Model.Profile p,String before,String after){if(!before.equals(after))p.academicRevision=revision(p)+1;}
    private Model.Account student(Long id){var a=r.accounts().findById(id).orElseThrow(()->Platform.fail(404,"Student not found"));if(a.role!=Model.Role.STUDENT)throw Platform.fail(404,"Student not found");return a;}
    private Model.Profile profile(Long id){return r.profiles().findByUserId(id).orElseThrow(()->Platform.fail(404,"Student profile not found"));}
    private Model.Profile lockedProfile(Long id){var p=r.profiles().lockByUserId(id).orElseThrow();em.refresh(p);return p;}
    private Model.Account lockedStudent(Long id){var a=r.accounts().lockAccount(id).orElseThrow();em.refresh(a);if(!a.enabled||Boolean.FALSE.equals(a.emailVerified))throw Platform.fail(409,"The student account is not active");return a;}
    private void requireStudent(Model.Account a){if(a.role!=Model.Role.STUDENT)throw Platform.fail(403,"Only students can submit academic documents");}
    private void requireStaff(Model.Account actor,Long university){if(actor.role==Model.Role.ADMIN)return;if(actor.role!=Model.Role.PLACEMENT_OFFICER||actor.universityId==null||!Objects.equals(actor.universityId,university))throw Platform.fail(403,"This academic record is outside your university");}
    private boolean current(Model.AcademicSubmission s,Model.Account a,Model.Profile p){return s.academicRevision==revision(p)&&Objects.equals(s.universityId,a.universityId);}
    private String status(Model.AcademicSubmission s,Model.Account a,Model.Profile p){return current(s,a,p)?s.status.name():"NEEDS_RESUBMISSION";}
    public Map<String,Object> summary(Model.Account a,Model.Profile p){var list=r.academicSubmissions().findByStudentIdOrderByIdDesc(a.id);if(list.isEmpty())return Platform.map("status","NOT_SUBMITTED","verified",false);var s=list.get(0);String state=status(s,a,p);return Platform.map("status",state,"verified",state.equals("VERIFIED"),"submittedAt",s.createdAt,"reviewedAt",s.reviewedAt);}
    public Map<String,Object> studentWorkspace(Model.Account a){requireStudent(a);var p=profile(a.id);return Platform.map("academicRevision",revision(p),"academicVerification",summary(a,p),"documents",r.academicDocuments().findByUserIdOrderByIdDesc(a.id).stream().map(this::documentView).toList(),"submissions",r.academicSubmissions().findByStudentIdOrderByIdDesc(a.id).stream().map(s->submissionView(s,a,p)).toList());}
    private Map<String,Object> documentView(Model.AcademicDocument d){return Platform.map("id",d.id,"kind",d.kind,"filename",d.filename,"mediaType",d.mediaType,"sizeBytes",d.sizeBytes,"createdAt",d.createdAt,"retained",r.academicSubmissions().documentReferences(d.id)>0);}
    private Map<String,Object> submissionView(Model.AcademicSubmission s,Model.Account a,Model.Profile p){return Platform.map("id",s.id,"version",s.version,"student",Platform.map("id",a.id,"name",a.name,"email",a.email,"department",a.department,"graduationYear",a.graduationYear),"universityId",s.universityId,"status",status(s,a,p),"decisionStatus",s.status,"createdAt",s.createdAt,"reviewedAt",s.reviewedAt,"reviewer",s.reviewerId==null?null:r.accounts().findById(s.reviewerId).map(x->x.name).orElse("Campus reviewer"),"reviewNote",s.reviewNote,"studentNote",s.studentNote,"snapshot",JSON.readTree(s.snapshotJson),"documents",s.documentIds.stream().map(id->r.academicDocuments().findById(id).orElseThrow()).map(this::documentView).toList());}
    public Map<String,Object> upload(Model.Account actor,Model.AcademicDocumentKind kind,MultipartFile file)throws IOException{
        requireStudent(actor);lockedProfile(actor.id);
        if(file.isEmpty()||file.getSize()>MAX_FILE)throw Platform.fail(400,"Choose a PDF, PNG or JPEG between 1 byte and 5 MB");
        var existing=r.academicDocuments().findByUserIdOrderByIdDesc(actor.id);if(existing.size()>=60||existing.stream().mapToLong(d->d.sizeBytes).sum()+file.getSize()>MAX_STORAGE)throw Platform.fail(400,"Academic document storage is full. Remove unused uploads before adding more.");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("document").replaceAll("[\\\\/\\p{Cntrl}]","_");if(filename.length()>180)filename=filename.substring(filename.length()-180);
        byte[] bytes=file.getBytes();String lower=filename.toLowerCase(Locale.ROOT),type,ext;
        try{
            if(lower.endsWith(".pdf")){try(var pdf=Loader.loadPDF(bytes)){if(pdf.isEncrypted()||pdf.getNumberOfPages()==0||pdf.getNumberOfPages()>100)throw Platform.fail(400,"Use an unencrypted PDF with 1–100 pages");}type="application/pdf";ext=".pdf";}
            else if(lower.endsWith(".png")||lower.endsWith(".jpg")||lower.endsWith(".jpeg")){
                try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw Platform.fail(400,"The image file is invalid");var reader=readers.next();try{reader.setInput(stream,true,true);String format=reader.getFormatName();boolean png=lower.endsWith(".png");if(!(png?format.equalsIgnoreCase("png"):format.equalsIgnoreCase("jpeg")))throw Platform.fail(400,"The file extension does not match the image");int w=reader.getWidth(0),h=reader.getHeight(0);if(w<=0||h<=0||(long)w*h>16000000)throw Platform.fail(400,"Use an image up to 16 megapixels");if(reader.read(0)==null)throw Platform.fail(400,"The image file is invalid");type=png?"image/png":"image/jpeg";ext=png?".png":".jpg";}finally{reader.dispose();}}
            }else throw Platform.fail(400,"Upload a PDF, PNG or JPEG document");
        }catch(org.springframework.web.server.ResponseStatusException e){throw e;}catch(Exception e){throw Platform.fail(400,"Could not read this document. Use a valid PDF, PNG or JPEG.");}
        var d=new Model.AcademicDocument();d.userId=actor.id;d.kind=kind;d.filename=filename;d.mediaType=type;d.sizeBytes=bytes.length;d.storageKey=UUID.randomUUID()+ext;Path path=path(d);Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCompletion(int state){if(state!=STATUS_COMMITTED)try{Files.deleteIfExists(path);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("An unused academic upload could not be removed");}}});
        r.academicDocuments().saveAndFlush(d);audit(actor,"ACADEMIC_DOCUMENT_UPLOADED",d.id.toString(),kind.name());return documentView(d);
    }
    public Model.AcademicDocument authorizedDocument(Model.Account actor,Long id){var d=r.academicDocuments().findById(id).orElseThrow(()->Platform.fail(404,"Academic document not found"));if(actor.role==Model.Role.STUDENT&&actor.id.equals(d.userId))return d;requireStaff(actor,student(d.userId).universityId);return d;}
    public Path path(Model.AcademicDocument d){Path p=directory.resolve(d.storageKey).normalize();if(!p.startsWith(directory))throw Platform.fail(400,"Invalid document path");return p;}
    public void delete(Model.Account actor,Long id){requireStudent(actor);lockedProfile(actor.id);var d=authorizedDocument(actor,id);if(r.academicSubmissions().documentReferences(id)>0)throw Platform.fail(409,"This document belongs to a submitted review and must be retained. Upload a corrected copy instead.");r.academicDocuments().delete(d);Path p=path(d);TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){try{Files.deleteIfExists(p);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("A deleted academic upload could not be removed from storage");}}});audit(actor,"ACADEMIC_DOCUMENT_DELETED",id.toString(),d.kind.name());}
    public Map<String,Object> submit(Model.Account actor,Inputs.SubmitAcademics input){
        requireStudent(actor);var a=lockedStudent(actor.id);var p=lockedProfile(actor.id);if(a.universityId==null)throw Platform.fail(400,"A registered university is required for campus review");
        if(input.academicRevision()!=revision(p))throw Platform.fail(409,"Your academic details changed. Refresh, check the saved record and submit again.");
        if(!Platform.schoolComplete(p.academics,true)||!Platform.schoolComplete(p.academics,false)||p.cgpa==null||p.academics==null||Platform.clean(p.academics.collegeInstitution).isEmpty()||Platform.clean(p.academics.degree).isEmpty()||Platform.clean(a.department).isEmpty()||a.graduationYear==null||p.academics.currentSemester==null||p.academics.activeBacklogs==null)throw Platform.fail(400,"Complete your 10th, 12th/diploma and college details, including semester, CGPA and active backlogs, before submitting.");
        var previous=r.academicSubmissions().findByStudentIdOrderByIdDesc(a.id);if(!previous.isEmpty()){var s=previous.get(0);if(current(s,a,p)&&s.status!=Model.AcademicReviewStatus.CHANGES_REQUESTED)throw Platform.fail(409,s.status==Model.AcademicReviewStatus.PENDING?"Your current record is already awaiting review.":"Your current record has already been verified.");}
        if(new HashSet<>(input.documentIds()).size()!=input.documentIds().size())throw Platform.fail(400,"Choose each document only once");
        Set<Model.AcademicDocumentKind> kinds=new HashSet<>();for(Long id:input.documentIds()){var d=r.academicDocuments().findById(id).orElseThrow(()->Platform.fail(400,"An academic document is missing"));if(!Objects.equals(d.userId,a.id))throw Platform.fail(403,"You can submit only your own documents");kinds.add(d.kind);}
        if(!kinds.containsAll(Set.of(Model.AcademicDocumentKind.TENTH,Model.AcademicDocumentKind.SECONDARY,Model.AcademicDocumentKind.COLLEGE)))throw Platform.fail(400,"Choose at least one document for 10th, 12th/diploma and college");
        var s=new Model.AcademicSubmission();s.studentId=a.id;s.universityId=a.universityId;s.academicRevision=revision(p);s.snapshotJson=snapshot(a,p);s.studentNote=Platform.clean(input.note());s.documentIds=new ArrayList<>(input.documentIds());r.academicSubmissions().saveAndFlush(s);audit(a,"ACADEMICS_SUBMITTED",s.id.toString(),"PENDING");
        notices.send(a.id,"Academic review submitted","Your saved academic record and supporting documents are waiting for campus review.","/academic-documents");
        return submissionView(s,a,p);
    }
    public List<Map<String,Object>> queue(Model.Account actor,String filter){requireStaff(actor,actor.universityId);if(!filter.isEmpty()&&!Set.of("ALL","PENDING","VERIFIED","CHANGES_REQUESTED","NEEDS_RESUBMISSION").contains(filter))throw Platform.fail(400,"Choose a valid academic review status");var list=actor.role==Model.Role.ADMIN?r.academicSubmissions().findAllByOrderByIdDesc():r.academicSubmissions().findByUniversityIdOrderByIdDesc(actor.universityId);return list.stream().filter(s->actor.role==Model.Role.ADMIN||Objects.equals(student(s.studentId).universityId,actor.universityId)).map(s->{var a=student(s.studentId);return submissionView(s,a,profile(a.id));}).filter(s->filter.isEmpty()||filter.equals("ALL")||s.get("status").equals(filter)).toList();}
    public Map<String,Object> review(Model.Account actor,Long id,Inputs.ReviewAcademics input){
        Long sid=r.academicSubmissions().studentId(id).orElseThrow(()->Platform.fail(404,"Academic submission not found"));var a=lockedStudent(sid);requireStaff(actor,a.universityId);var p=lockedProfile(sid);var s=r.academicSubmissions().lockSubmission(id).orElseThrow();em.refresh(s);requireStaff(actor,s.universityId);
        if(s.version!=input.version()||!current(s,a,p)||s.status!=Model.AcademicReviewStatus.PENDING)throw Platform.fail(409,"This submission changed, was already reviewed, or needs resubmission. Refresh the review list.");
        String note=Platform.clean(input.note());if(input.status().equals("CHANGES_REQUESTED")&&note.isEmpty())throw Platform.fail(400,"Explain which academic details or documents need correction");
        s.status=Model.AcademicReviewStatus.valueOf(input.status());s.reviewNote=note;s.reviewerId=actor.id;s.reviewedAt=Instant.now();r.academicSubmissions().saveAndFlush(s);audit(actor,"ACADEMICS_REVIEWED",s.id.toString(),s.status.name());
        notices.send(a.id,s.status==Model.AcademicReviewStatus.VERIFIED?"Academic record verified":"Academic corrections requested",s.status==Model.AcademicReviewStatus.VERIFIED?"Your submitted academic record was reviewed and approved by campus staff.":note,"/academic-documents");return submissionView(s,a,p);
    }
    private void audit(Model.Account actor,String action,String target,String detail){var e=new Model.Audit();e.actorId=actor.id;e.action=action;e.target=target;e.detail=detail;r.audits().save(e);}
}

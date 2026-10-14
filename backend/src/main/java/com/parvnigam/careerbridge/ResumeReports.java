package com.parvnigam.careerbridge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/student")
public class ResumeReports {
    final Platform platform;
    final Repositories.Resumes resumes;
    final Repositories.ResumeReports reports;
    final CareerService career;
    final JsonMapper json = JsonMapper.builder().build();
    public ResumeReports(Platform platform, Repositories.Resumes resumes, Repositories.ResumeReports reports, CareerService career) {
        this.platform=platform; this.resumes=resumes; this.reports=reports; this.career=career;
    }
    public record Request(Long jobId, @Size(max=150) String targetTitle, @Size(max=12000) String jobDescription, Boolean aiSuggestions) {}
    Model.Resume ownedResume(Model.Account actor, Long id) {
        Model.Resume resume=resumes.findById(id).orElseThrow(() -> Platform.fail(404,"Resume not found"));
        if(!actor.id.equals(resume.userId)) throw Platform.fail(403,"Only your own resume can be analysed");
        return resume;
    }
    Model.ResumeReport ownedReport(Model.Account actor, Long id) {
        Model.ResumeReport report=reports.findById(id).orElseThrow(() -> Platform.fail(404,"Report not found"));
        if(!actor.id.equals(report.userId)) throw Platform.fail(403,"This report belongs to another student");
        return report;
    }
    @GetMapping("/resumes/{id}/text")
    Object text(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {
        Model.Resume resume=ownedResume(platform.actor(jwt),id);
        return Platform.map("text",Platform.clean(resume.extractedText),"filename",resume.filename);
    }
    @PostMapping("/resumes/{id}/reports") @ResponseStatus(HttpStatus.CREATED)
    Object create(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id,@Valid @RequestBody Request request) throws Exception {
        Model.Account actor=platform.actor(jwt);
        Model.Resume resume=ownedResume(actor,id);
        String text=Platform.clean(resume.extractedText);
        if(text.isBlank()) throw Platform.fail(400,"This version has no extracted text. Upload a text-based resume");
        String description=Platform.clean(request.jobDescription());
        if(request.jobId()!=null&&!description.isBlank()) throw Platform.fail(400,"Choose an opening or paste a description, not both");
        if(request.jobId()==null&&!description.isBlank()&&description.length()<30)
            throw Platform.fail(400,"Paste a job description with at least 30 characters");
        String title="General resume review", method="NO_TARGET", type="GENERAL";
        List<String> skills=List.of();
        if(request.jobId()!=null) {
            Model.Job job=platform.job(request.jobId());
            if(!platform.canSee(actor,job)) throw Platform.fail(403,"This opening is for another university");
            if(!platform.active(job)) throw Platform.fail(400,"Choose an active opening");
            title=job.title; description=Platform.clean(job.description); type="OPENING";
            skills=job.requiredSkills.isEmpty()?ResumeScorer.detectSkills(description):job.requiredSkills;
            method=job.requiredSkills.isEmpty()?"DESCRIPTION_SKILL_DICTIONARY":"DECLARED_REQUIRED_SKILLS";
        } else if(!description.isBlank()) {
            title=Platform.clean(request.targetTitle());
            if(title.isBlank()) title="Pasted job description";
            type="PASTED"; method="DESCRIPTION_SKILL_DICTIONARY";
            skills=ResumeScorer.detectSkills(description);
        }
        ResumeScorer.Readiness readiness=ResumeScorer.readiness(text);
        ResumeScorer.Match match=ResumeScorer.match(text,skills,method);
        String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
            json.writeValueAsString(List.of(type,request.jobId()==null?"":request.jobId(),title,description,match.requiredSkills()))
                .getBytes(StandardCharsets.UTF_8)));
        Map<String,Object> target=Platform.map("type",type,"jobId",request.jobId(),"title",title,"fingerprint",fingerprint);
        Map<String,Object> ai=Platform.map("status","NOT_REQUESTED","suggestions",List.of());
        if(Boolean.TRUE.equals(request.aiSuggestions())) {
            // Only project/experience sections are shared. Mask common contacts/URLs; this is not full anonymisation.
            String evidence=ResumeScorer.evidenceText(text);
            if(evidence.length()>8000) evidence=evidence.substring(0,8000);
            evidence=evidence.replaceAll("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}","[email removed]")
                .replaceAll("(?:https?://|www\\.)\\S+","[link removed]")
                .replaceAll("(?<!\\d)(?:\\+\\d{1,3}[ -]?)?(?:\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}|\\d{5}[ -]?\\d{5})(?!\\d)","[phone removed]");
            Map<String,Object> remote=career.remote("/resume-feedback",Platform.map("evidenceText",evidence,"targetTitle",title,
                "jobDescription",description,"missingSkills",match.missingSkills(),"localSuggestions",readiness.suggestions()));
            ai=validatedAi(remote);
        }
        Map<String,Object> snapshot=Platform.map("scorerVersion",ResumeScorer.VERSION,"target",target,
            "readiness",readiness,"match",match,"ai",ai,
            "limitations",List.of("Estimated content readiness, not an employer ATS score or a shortlisting prediction.",
                "Text checks cannot validate columns, tables, page layout, factual accuracy or skill proficiency.",
                "Pasted descriptions use a limited skill dictionary. Review the identified skills before relying on the match score."));
        Model.ResumeReport report=new Model.ResumeReport(); report.userId=actor.id; report.resumeId=id;
        report.filename=resume.filename; report.reportJson=json.writeValueAsString(snapshot);
        return view(reports.save(report));
    }
    Map<String,Object> validatedAi(Map<String,Object> remote) {
        if(remote!=null&&"OPENAI".equals(remote.get("status"))&&remote.get("suggestions") instanceof List<?> items
            && !items.isEmpty()&&items.size()<=5) {
            List<Map<String,Object>> safe=new ArrayList<>();
            for(Object item:items) {
                if(!(item instanceof Map<?,?> value)||!(value.get("issue") instanceof String issue)
                    ||!(value.get("recommendation") instanceof String recommendation)||issue.isBlank()||recommendation.isBlank()
                    ||issue.length()>1000||recommendation.length()>1500)
                    return Platform.map("status","UNAVAILABLE","suggestions",List.of());
                safe.add(Platform.map("issue",issue,"recommendation",recommendation));
            }
            return Platform.map("status","OPENAI","suggestions",safe);
        }
        return Platform.map("status","UNAVAILABLE","suggestions",List.of());
    }
    @GetMapping("/resume-reports")
    Object list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page) {
        if(page<0||page>100000) throw Platform.fail(400,"Invalid report page");
        return reports.findByUserIdOrderByCreatedAtDescIdDesc(platform.actor(jwt).id,PageRequest.of(page,20)).stream().map(this::view).toList();
    }
    @GetMapping("/resume-reports/{id}")
    Object get(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {return view(ownedReport(platform.actor(jwt),id));}
    @DeleteMapping("/resume-reports/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable Long id) {reports.delete(ownedReport(platform.actor(jwt),id));}
    Object view(Model.ResumeReport report) {
        return Platform.map("id",report.id,"resumeId",report.resumeId,"filename",report.filename,"createdAt",report.createdAt,
            "report",json.readValue(report.reportJson,Object.class));
    }
}

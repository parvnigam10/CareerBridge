package com.parvnigam.careerbridge;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api")
public class AcademicReviewController {
    private final Platform p;private final AcademicReviewService reviews;
    AcademicReviewController(Platform p,AcademicReviewService reviews){this.p=p;this.reviews=reviews;}
    @GetMapping("/student/academic-documents") Object workspace(@AuthenticationPrincipal Jwt jwt){return reviews.studentWorkspace(p.actor(jwt));}
    @PostMapping("/student/academic-documents") @ResponseStatus(HttpStatus.CREATED) Object upload(@AuthenticationPrincipal Jwt jwt,@RequestParam("kind") Model.AcademicDocumentKind kind,@RequestParam("file") MultipartFile file)throws Exception{return reviews.upload(p.actor(jwt),kind,file);}
    @DeleteMapping("/student/academic-documents/{id}") Object delete(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id){reviews.delete(p.actor(jwt),id);return Platform.map("ok",true);}
    @PostMapping("/student/academic-submissions") @ResponseStatus(HttpStatus.CREATED) Object submit(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Inputs.SubmitAcademics d){return reviews.submit(p.actor(jwt),d);}
    @GetMapping("/placement/academic-reviews") Object queue(@AuthenticationPrincipal Jwt jwt,@RequestParam(name="status",defaultValue="PENDING") String status){return reviews.queue(p.actor(jwt),status);}
    @PatchMapping("/placement/academic-reviews/{id}") Object review(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id,@Valid @RequestBody Inputs.ReviewAcademics d){return reviews.review(p.actor(jwt),id,d);}
    @GetMapping("/academic-documents/{id}/download") Object download(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id){var d=reviews.authorizedDocument(p.actor(jwt),id);var path=reviews.path(d);if(!Files.isRegularFile(path))throw Platform.fail(404,"The document is unavailable. Contact your campus administrator.");return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mediaType)).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.filename,StandardCharsets.UTF_8).build().toString()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CACHE_CONTROL,"no-store").body(new FileSystemResource(path));}
}

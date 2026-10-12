package com.parvnigam.careerbridge;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class CampusDriveController {
    private final Platform p;private final CampusDriveService drives;
    CampusDriveController(Platform p,CampusDriveService drives){this.p=p;this.drives=drives;}
    @GetMapping("/drives") Object list(@AuthenticationPrincipal Jwt jwt){return drives.list(p.actor(jwt));}
    @GetMapping("/drives/{id}") Object detail(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id){return drives.detail(p.actor(jwt),id);}
    @PostMapping("/placement/drives") @ResponseStatus(HttpStatus.CREATED) Object create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Inputs.Drive d){return drives.save(p.actor(jwt),null,d);}
    @PutMapping("/placement/drives/{id}") Object edit(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id,@Valid @RequestBody Inputs.Drive d){return drives.save(p.actor(jwt),id,d);}
    @PatchMapping("/placement/drives/{id}/status") Object status(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id,@Valid @RequestBody Inputs.DriveStatus d){return drives.changeStatus(p.actor(jwt),id,d);}
    @PostMapping("/student/drives/{id}/registration") Object register(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id){return drives.register(p.actor(jwt),id);}
    @DeleteMapping("/student/drives/{id}/registration") Object withdraw(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id){return drives.withdraw(p.actor(jwt),id);}
    @PatchMapping("/placement/drives/{id}/results/{resultId}") Object result(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id,@PathVariable("resultId") Long resultId,@Valid @RequestBody Inputs.DriveResult d){return drives.result(p.actor(jwt),id,resultId,d);}
    @GetMapping("/drives/{id}/export") Object export(@AuthenticationPrincipal Jwt jwt,@PathVariable("id") Long id){return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=drive-participants.csv").body(drives.export(p.actor(jwt),id));}
}

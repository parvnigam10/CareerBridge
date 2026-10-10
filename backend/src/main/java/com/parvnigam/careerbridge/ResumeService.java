package com.parvnigam.careerbridge;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeService {
    final Store r;final Path directory;
    public ResumeService(Store store,@Value("${app.upload-dir}") String path) throws IOException {r=store;directory=Path.of(path).toAbsolutePath().normalize();Files.createDirectories(directory);}
    @Transactional
    public Model.Resume upload(Model.Account student,MultipartFile file) throws Exception {
        if(file.isEmpty()||file.getSize()>5*1024*1024)throw Platform.fail(400,"Choose a resume between 1 byte and 5 MB");
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("resume.txt").replaceAll("[\\\\/\r\n]","_");
        if(filename.length()>180)filename=filename.substring(filename.length()-180);
        byte[] bytes=file.getBytes();String lower=filename.toLowerCase();String text;String type;String ext;
        if(lower.endsWith(".pdf")){if(!new String(bytes,0,Math.min(bytes.length,5),StandardCharsets.US_ASCII).startsWith("%PDF-"))throw Platform.fail(400,"Invalid PDF file");try(var pdf=Loader.loadPDF(bytes)){text=new PDFTextStripper().getText(pdf);}type="application/pdf";ext=".pdf";}
        else if(lower.endsWith(".docx")){text=docx(bytes);type="application/vnd.openxmlformats-officedocument.wordprocessingml.document";ext=".docx";}
        else if(lower.endsWith(".txt")){text=new String(bytes,StandardCharsets.UTF_8);if(text.indexOf('\0')>=0)throw Platform.fail(400,"Invalid text file");type="text/plain";ext=".txt";}
        else throw Platform.fail(400,"Upload a PDF, DOCX or TXT resume");
        if(text.trim().isEmpty())throw Platform.fail(400,"No text found. Upload a text-based resume; scanned PDFs need OCR first");
        Set<String> vocabulary=new LinkedHashSet<>(List.of("Java","Spring Boot","SQL","Python","React","JavaScript","TypeScript","HTML","CSS","Git","Docker","Redis","PostgreSQL","MySQL","DSA","Machine Learning","REST APIs","AWS","JUnit","Linux","MongoDB","Kubernetes","Excel","Communication"));
        r.jobs().findAll().forEach(j->vocabulary.addAll(j.requiredSkills));String normalized=text.toLowerCase();
        List<String> found=vocabulary.stream().filter(s->java.util.regex.Pattern.compile("(?<![a-z0-9])"+java.util.regex.Pattern.quote(s.toLowerCase())+"(?![a-z0-9])").matcher(normalized).find()).toList();
        Model.Resume v=new Model.Resume();v.userId=student.id;v.filename=filename;v.storageKey=UUID.randomUUID()+ext;v.mediaType=type;v.sizeBytes=bytes.length;v.extractedText=text.substring(0,Math.min(text.length(),60000));v.detectedSkills=new ArrayList<>(found);
        Files.write(directory.resolve(v.storageKey),bytes,StandardOpenOption.CREATE_NEW);
        try{return r.resumes().saveAndFlush(v);}catch(RuntimeException e){Files.deleteIfExists(directory.resolve(v.storageKey));throw e;}
    }
    String docx(byte[] bytes) throws Exception {
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;int count=0;while((entry=zip.getNextEntry())!=null){if(++count>1000)throw Platform.fail(400,"DOCX has too many entries");if(!entry.getName().equals("word/document.xml"))continue;
            byte[] xml=zip.readNBytes(5*1024*1024+1);if(xml.length>5*1024*1024)throw Platform.fail(400,"DOCX document is too large");
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));var nodes=doc.getElementsByTagName("w:p");StringBuilder text=new StringBuilder();for(int i=0;i<nodes.getLength();i++)text.append(nodes.item(i).getTextContent()).append('\n');return text.toString();}}
        throw Platform.fail(400,"Invalid DOCX file");
    }
    public Map<String,Object> view(Model.Resume v){return Platform.map("id",v.id,"filename",v.filename,"sizeBytes",v.sizeBytes,"createdAt",v.createdAt,"detectedSkills",v.detectedSkills);}
    public Path path(Model.Resume v){Path p=directory.resolve(v.storageKey).normalize();if(!p.startsWith(directory))throw Platform.fail(400,"Invalid resume path");return p;}
    public Model.Resume authorized(Model.Account a,Long id){Model.Resume v=r.resumes().findById(id).orElseThrow(()->Platform.fail(404,"Resume not found"));if(a.id.equals(v.userId)||a.role==Model.Role.ADMIN)return v;
        if(a.role==Model.Role.RECRUITER&&r.applications().findByStudentIdOrderByCreatedAtDesc(v.userId).stream().anyMatch(app->Objects.equals(app.resumeId,id)&&r.jobs().findById(app.jobId).map(j->j.recruiterId.equals(a.id)).orElse(false)))return v;
        throw Platform.fail(403,"You cannot access this resume");}
    @Transactional public void delete(Model.Account a,Long id)throws IOException{Model.Resume v=authorized(a,id);if(!v.userId.equals(a.id))throw Platform.fail(403,"Only the owner can delete a resume");if(r.applications().findByStudentIdOrderByCreatedAtDesc(a.id).stream().anyMatch(x->Objects.equals(x.resumeId,id)))throw Platform.fail(409,"This version is attached to an application and must be retained");r.resumes().delete(v);Files.deleteIfExists(path(v));}
}

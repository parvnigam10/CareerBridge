package com.parvnigam.careerbridge;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;

@Service
public class CareerService {
    final Store r;final StringRedisTemplate redis;final boolean cacheEnabled;final String aiUrl;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();
    public CareerService(Store r,StringRedisTemplate redis,@Value("${app.redis-enabled}") boolean cacheEnabled,@Value("${app.ai-url}")String aiUrl){this.r=r;this.redis=redis;this.cacheEnabled=cacheEnabled;this.aiUrl=aiUrl;}
    Map<String,Object> remote(String path,Object payload){if(aiUrl.isBlank())return null;try{HttpRequest req=HttpRequest.newBuilder(URI.create(aiUrl+path)).timeout(Duration.ofSeconds(35)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();var response=http.send(req,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)return null;return json.readValue(response.body(),new TypeReference<Map<String,Object>>(){});}catch(Exception e){return null;}}
    public Map<String,Object> github(Model.Account a){Model.Profile p=r.profiles().findByUserId(a.id).orElseThrow();String username=p.githubUsername;if(!username.matches("[A-Za-z0-9-]{1,39}"))throw Platform.fail(400,"Enter a valid GitHub username in your profile");
        String key="careerbridge:github:"+username.toLowerCase();String body=null;
        if(cacheEnabled)try{body=redis.opsForValue().get(key);}catch(Exception ignored){}
        if(body==null)try{var response=http.send(HttpRequest.newBuilder(URI.create("https://api.github.com/users/"+username)).timeout(Duration.ofSeconds(10)).header("User-Agent","CareerBridge").header("Accept","application/vnd.github+json").GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw Platform.fail(502,"GitHub did not return this profile. Check the username or try later");body=response.body();if(cacheEnabled)try{redis.opsForValue().set(key,body,Duration.ofMinutes(15));}catch(Exception ignored){}}catch(java.io.IOException|InterruptedException e){throw Platform.fail(502,"GitHub is unavailable. Your saved profile was preserved");}
        try{var node=json.readTree(body);Map<String,Object> result=Platform.map("username",username,"publicRepos",node.path("public_repos").asInt(),"followers",node.path("followers").asInt(),"url",node.path("html_url").asText(),"bio",node.path("bio").asText(""));p.githubSnapshot=json.writeValueAsString(result);p.codingSyncedAt=Instant.now();r.profiles().save(p);return result;}catch(Exception e){throw Platform.fail(502,"Could not read GitHub profile");}}
    public List<Map<String,Object>> questions(String skill){String s=skill.toLowerCase();List<Map<String,Object>> qs=new ArrayList<>();
        if(s.contains("java")||s.contains("spring")){
            qs.add(question("Explain dependency injection and constructor injection in Spring.",List.of("dependency","constructor","container"),"Explain who creates an object and supplies its dependencies."));
            qs.add(question("How do an interface and an abstract class differ in Java?",List.of("interface","abstract","implements"),"Discuss contracts, shared behaviour and inheritance."));
            qs.add(question("Explain HashMap lookup and what happens during a hash collision.",List.of("hash","bucket","equals"),"Cover hashCode, equals and collision handling."));
            qs.add(question("How would you design a REST endpoint that creates a job opening?",List.of("post","validation","service"),"Describe the request, validation, service and response status."));
            qs.add(question("How do you secure an endpoint so only its owner can update it?",List.of("authentication","authorization","owner"),"Role checks alone do not establish resource ownership."));
        }else if(s.contains("sql")||s.contains("database")){
            qs.add(question("Explain INNER JOIN and LEFT JOIN with a recruitment example.",List.of("join","left","null"),"Use students and applications as example tables."));
            qs.add(question("What is a database index and what are its trade-offs?",List.of("index","read","write"),"Discuss query speed, storage and update cost."));
            qs.add(question("Explain transactions and the ACID properties.",List.of("atomicity","consistency","isolation","durability"),"Give an example involving an application and its history."));
            qs.add(question("How would you prevent a student applying twice to one job?",List.of("unique","student","job"),"Enforce the invariant in the database as well as in the service."));
            qs.add(question("How would you find each university's placement rate?",List.of("group","count","distinct"),"Count distinct hired students, rather than offers."));
        }else{
            qs.add(question("Describe a project where you used "+skill+" and explain your contribution.",List.of("problem","approach","result"),"State a concrete problem, your action and the measured result."));
            qs.add(question("How would you debug a failing feature in a "+skill+" application?",List.of("reproduce","logs","test"),"Start with reproducible evidence."));
            qs.add(question("How would you test and deploy your implementation?",List.of("test","deployment","monitor"),"Cover success cases, failure cases and monitoring."));
            qs.add(question("Describe a performance bottleneck and how you would investigate it.",List.of("measure","profile","optimize"),"Measure before changing the design."));
            qs.add(question("Tell me about a disagreement in a team and how you resolved it.",List.of("team","discuss","result"),"Use a specific example with an outcome."));
        }return qs;
    }
    Map<String,Object> question(String text,List<String> keywords,String tip){return Platform.map("question",text,"keywords",keywords,"tip",tip);}
    public Model.MockSession start(Model.Account a,Inputs.Mock d){Map<String,Object> generated=remote("/questions",Platform.map("role",d.targetRole(),"skill",d.skill()));Object qs=generated!=null&&generated.get("questions") instanceof List<?> list&&!list.isEmpty()?generated.get("questions"):questions(d.skill());
        Model.MockSession m=new Model.MockSession();m.userId=a.id;m.targetRole=d.targetRole();m.skill=d.skill();m.engine=generated==null?"LOCAL_QUESTION_BANK":String.valueOf(generated.getOrDefault("engine","PYTHON"));m.questionsJson=json.writeValueAsString(qs);r.mocks().save(m);return m;}
    public Map<String,Object> mockView(Model.MockSession m){return Platform.map("id",m.id,"targetRole",m.targetRole,"skill",m.skill,"engine",m.engine,"status",m.status,"createdAt",m.createdAt,"score",m.score,"questions",decode(m.questionsJson),"answers",decode(m.answersJson),"feedback",decode(m.feedbackJson));}
    Object decode(String s){return s==null?null:json.readValue(s,Object.class);}
    @SuppressWarnings("unchecked") public Model.MockSession submit(Model.Account a,Long id,List<String> answers){Model.MockSession m=r.mocks().findById(id).orElseThrow(()->Platform.fail(404,"Practice session not found"));if(!m.userId.equals(a.id))throw Platform.fail(403,"This is another student's session");if(!m.status.equals("IN_PROGRESS"))throw Platform.fail(409,"This practice session was already submitted");
        List<Map<String,Object>> qs=(List<Map<String,Object>>)decode(m.questionsJson);if(answers.size()!=qs.size()||answers.stream().anyMatch(x->x==null||x.length()>6000))throw Platform.fail(400,"Provide one answer per question, each under 6000 characters");
        List<Map<String,Object>> feedback=new ArrayList<>();double sum=0;for(int i=0;i<qs.size();i++){String answer=answers.get(i).trim();List<String> words=(List<String>)qs.get(i).get("keywords");if(words==null)words=List.of();final String lower=answer.toLowerCase();List<String> covered=words.stream().filter(w->lower.contains(w.toLowerCase())).toList();double coverage=words.isEmpty()?0:covered.size()*1.0/words.size();int score=answer.isEmpty()?0:(int)Math.round(coverage*70+Math.min(1,answer.split("\\s+").length/70.0)*30);sum+=score;feedback.add(Platform.map("score",score,"coveredKeywords",covered,"suggestion",qs.get(i).get("tip")));}
        m.score=Math.round(sum/qs.size()*10)/10.0;m.answersJson=json.writeValueAsString(answers);m.feedbackJson=json.writeValueAsString(Platform.map("method","Keyword coverage and answer length; a practice aid, not a validated interview assessment","items",feedback));m.status="COMPLETED";return r.mocks().save(m);}
    public Map<String,Object> learning(List<Map<String,Object>> recommendations){Map<String,Integer> demand=new LinkedHashMap<>();recommendations.stream().limit(6).forEach(j->{Map<?,?> match=(Map<?,?>)j.get("match");((List<?>)match.get("missingSkills")).forEach(x->demand.merge(x.toString(),1,Integer::sum));});
        List<Map<String,Object>> gaps=demand.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).map(e->Platform.map("skill",e.getKey(),"demand",e.getValue(),"steps",List.of("Learn the core concepts and write small examples","Build a small feature using "+e.getKey(),"Add tests and document the feature","Practise interview questions and update your profile"))).toList();return Platform.map("gaps",gaps,"method","Missing skills across your top six job matches");}
    @SuppressWarnings("unchecked") public List<Map<String,Object>> rank(Model.Account student,List<Map<String,Object>> jobs){Model.Profile p=r.profiles().findByUserId(student.id).orElseThrow();Map<String,Object> response=remote("/rank",Platform.map("profileText",p.headline+" "+p.about+" "+String.join(" ",p.skills),"jobs",jobs.stream().map(j->Platform.map("id",j.get("id"),"text",j.get("title")+" "+j.get("description")+" "+String.join(" ",(List<String>)j.get("requiredSkills")))).toList()));if(response==null||!(response.get("scores") instanceof Map<?,?> scores))return jobs;
        for(Map<String,Object> j:jobs){Object value=scores.get(j.get("id").toString());if(value instanceof Number n){double semantic=Math.max(0,Math.min(1,n.doubleValue()));double score=((Number)((Map<?,?>)j.get("match")).get("score")).doubleValue();j.put("semanticSimilarity",Math.round(semantic*1000)/10.0);j.put("rankingScore",Math.round((score*.75+semantic*100*.25)*10)/10.0);}}return jobs.stream().sorted(Comparator.comparingDouble((Map<String,Object>j)->((Number)j.getOrDefault("rankingScore",((Map<?,?>)j.get("match")).get("score"))).doubleValue()).reversed()).toList();}
}

package com.parvnigam.careerbridge;

import java.util.*;

/** One assessment shared by job views, candidate comparisons, invitations and application submission. */
public final class Eligibility {
    private Eligibility() {}
    public record Check(String key,String label,String requirement,String actual,String status,String message) {}
    public record Report(boolean eligible,String status,List<Check> checks) {
        String blockedMessage() {
            return "Eligibility requirements: "+String.join("; ",checks.stream().filter(c->!c.status().equals("MET")).map(Check::message).toList());
        }
    }
    static String normalizeBranch(String value) {return Platform.clean(value).replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    static String number(Number value) {return value==null?"Not provided":java.math.BigDecimal.valueOf(value.doubleValue()).stripTrailingZeros().toPlainString();}
    static boolean positive(Double value) {return value!=null&&value>0;}

    public static Report assess(Model.Account student,Model.Profile profile,Model.Job job) {
        List<Check> checks=new ArrayList<>();
        school(checks,profile.academics,true,job.minTenthPercentage);
        school(checks,profile.academics,false,job.minSecondaryPercentage);
        if(positive(job.minCgpa))minimum(checks,"cgpa","College CGPA",job.minCgpa,profile.cgpa," / 10","Add your college CGPA in Academic details");
        if(positive(job.minExperience))minimum(checks,"experience","Experience",job.minExperience,profile.experienceYears," years","Add your experience in your profile");
        if(job.allowedBranches!=null&&!job.allowedBranches.isEmpty()) {
            String branch=Platform.clean(student.department);
            boolean missing=branch.isEmpty(),matches=job.allowedBranches.stream().anyMatch(b->normalizeBranch(b).equals(normalizeBranch(branch)));
            String requirement=String.join(", ",job.allowedBranches.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList());
            checks.add(new Check("branch","Branch",requirement,missing?"Not provided":branch,missing?"MISSING":matches?"MET":"NOT_MET",
                missing?"Add your branch in Academic details":matches?"Your branch is accepted":"Your branch must be one of: "+requirement));
        }
        if(job.allowedGraduationYears!=null&&!job.allowedGraduationYears.isEmpty()) {
            boolean missing=student.graduationYear==null,matches=job.allowedGraduationYears.contains(student.graduationYear);
            String requirement=String.join(", ",job.allowedGraduationYears.stream().sorted().map(Object::toString).toList());
            checks.add(new Check("graduationYear","Graduation year",requirement,number(student.graduationYear),missing?"MISSING":matches?"MET":"NOT_MET",
                missing?"Add your graduation year in Academic details":matches?"Your graduation year is accepted":"Graduation year must be one of: "+requirement));
        }
        if(job.maxActiveBacklogs!=null) {
            Integer backlogs=profile.academics==null?null:profile.academics.activeBacklogs;
            boolean missing=backlogs==null,matches=!missing&&backlogs<=job.maxActiveBacklogs;
            checks.add(new Check("activeBacklogs","Active backlogs","At most "+job.maxActiveBacklogs,number(backlogs),missing?"MISSING":matches?"MET":"NOT_MET",
                missing?"Add your active backlog count in Academic details (enter 0 if you have none)":matches?"Your active backlog count meets the limit":"Active backlogs must be at most "+job.maxActiveBacklogs+"; yours: "+backlogs));
        }
        if(job.universityId!=null) {
            boolean matches=Objects.equals(job.universityId,student.universityId);
            checks.add(new Check("university","University","Opening's registered university",student.universityId==null?"Not provided":matches?"Same university":"Different university",matches?"MET":"NOT_MET",
                matches?"This campus opening includes your university":"This opening is restricted to another university"));
        }
        boolean failed=checks.stream().anyMatch(c->c.status().equals("NOT_MET"));
        boolean eligible=checks.stream().allMatch(c->c.status().equals("MET"));
        return new Report(eligible,eligible?"ELIGIBLE":failed?"NOT_ELIGIBLE":"DETAILS_REQUIRED",List.copyOf(checks));
    }

    static void minimum(List<Check> checks,String key,String label,double minimum,Double actual,String unit,String missingMessage) {
        boolean missing=actual==null,matches=!missing&&actual>=minimum;
        String requirement="At least "+number(minimum)+unit;
        checks.add(new Check(key,label,requirement,missing?"Not provided":number(actual)+unit,missing?"MISSING":matches?"MET":"NOT_MET",
            missing?missingMessage:matches?label+" meets the minimum":label+" must be at least "+number(minimum)+unit+"; yours: "+number(actual)+unit));
    }

    static void school(List<Check> checks,Model.Academics academics,boolean tenth,Double minimum) {
        if(!positive(minimum))return;
        String key=tenth?"tenthPercentage":"secondaryPercentage",label=tenth?"10th percentage":"12th / diploma percentage";
        String type=academics==null?null:tenth?academics.tenthScoreType:academics.secondaryScoreType;
        Double score=academics==null?null:tenth?academics.tenthScore:academics.secondaryScore;
        String result=Platform.schoolResult(academics,tenth),requirement="At least "+number(minimum)+"%";
        if(academics==null||type==null||result.isEmpty()||!tenth&&academics.secondaryQualification==null) {
            checks.add(new Check(key,label,requirement,"Not provided","MISSING","Add your "+(tenth?"10th":"12th / diploma")+" percentage in Academic details"));return;
        }
        if(!type.equals("PERCENTAGE")) {
            checks.add(new Check(key,label,requirement,result,"REVIEW_REQUIRED","This opening requires a "+(tenth?"10th":"12th / diploma")+" percentage; your result uses "+type+". Provide an institution-reported percentage or ask the recruiter to review the requirement."));return;
        }
        minimum(checks,key,label,minimum,score,"%","Add your "+(tenth?"10th":"12th / diploma")+" percentage in Academic details");
    }
}

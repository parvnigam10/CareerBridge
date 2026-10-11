package com.parvnigam.careerbridge;
import org.springframework.stereotype.Component;
@Component
public record Store(Repositories.Accounts accounts,Repositories.Universities universities,
    Repositories.Profiles profiles,Repositories.AcademicDocuments academicDocuments,Repositories.AcademicSubmissions academicSubmissions,Repositories.Projects projects,Repositories.Educations educations,
    Repositories.Experiences experiences,Repositories.Companies companies,Repositories.Jobs jobs,
    Repositories.Applications applications,Repositories.ApplicationEvents events,Repositories.SavedJobs saves,
    Repositories.Resumes resumes,Repositories.Notices notices,Repositories.Interviews interviews,
    Repositories.Mocks mocks,Repositories.Audits audits) {}

import {Field} from './ui';

export function EligibilityFields({job}) {
  return <fieldset className="academic-section wide eligibility-fields"><legend>Who can apply</legend>
    <p className="muted small">Leave school cutoffs and backlog limits blank for no restriction. Blank branch and year lists allow everyone. A backlog limit of 0 allows no active backlogs.</p>
    <div className="form-grid">
      <Field label="Minimum 10th percentage" name="minTenthPercentage" type="number" min={0} max={100} step="0.01" defaultValue={job.minTenthPercentage??''} placeholder="No minimum"/>
      <Field label="Minimum 12th / diploma percentage" name="minSecondaryPercentage" type="number" min={0} max={100} step="0.01" defaultValue={job.minSecondaryPercentage??''} placeholder="No minimum"/>
      <Field label="Minimum CGPA (out of 10)" name="minCgpa" type="number" min={0} max={10} step="0.01" defaultValue={job.minCgpa??0}/>
      <Field label="Maximum active backlogs" name="maxActiveBacklogs" type="number" min={0} max={50} defaultValue={job.maxActiveBacklogs??''} placeholder="No limit"/>
      <Field label="Allowed branches, separated by commas" name="allowedBranches" maxLength={3000} defaultValue={job.allowedBranches?.join(', ')||''} placeholder="Computer Science, Information Technology" wide/>
      <small className="wide muted">Use the full branch names students enter in their profiles. Matching ignores letter case and extra spaces.</small>
      <Field label="Allowed graduation years, separated by commas" name="allowedGraduationYears" maxLength={200} defaultValue={job.allowedGraduationYears?.join(', ')||''} placeholder="2027, 2028"/>
      <Field label="Minimum experience (years)" name="minExperience" type="number" min={0} step="0.1" defaultValue={job.minExperience??0}/>
      <small className="wide muted">Percentage cutoffs apply to the recorded percentage for either 12th or diploma. Results in CGPA or letter grades need an institution-reported percentage or recruiter review.</small>
    </div>
  </fieldset>;
}

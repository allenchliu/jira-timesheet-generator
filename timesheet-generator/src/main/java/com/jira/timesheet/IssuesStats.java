package com.jira.timesheet;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang.time.DateUtils;

import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.WorkLog;

/**
 * Issues statistics for one date in calendar.
 * 
 * @author Maros Vranec
 */
public class IssuesStats {
    /** Found issues for this stats date. */
    private final Set<String> issues = new LinkedHashSet<String>();
    /** Logged work in seconds for this date. */
    private int seconds;

    /**
     * Add an issue to this date statistics.
     * 
     * @param date
     *            Date for determining the logged seconds.
     * @param issue
     *            Issue to be added.
     * @param username
     *            Username to find out logged work.
     */
    public void addIssue(Date date, Issue issue, String username) {
        if (issues.contains(issue.getKey())) {
            return;
        }
        issues.add(issue.getKey());
        for (WorkLog workLog : issue.getWorkLogs()) {
            if (workLog.getAuthor().getName().equals(username)) {
                if (date.equals(DateUtils.truncate(workLog.getStarted(), Calendar.DATE))) {
                    seconds += workLog.getTimeSpentSeconds();
                }
            }
        }
    }

    /**
     * @return Found issues for this stats date.
     */
    public String getIssues() {
        return issues.toString().replace("[", "").replace("]", "");
    }

    /**
     * @return Logged work in seconds for this date.
     */
    public int getLoggedSecondsOfWork() {
        return seconds;
    }
}

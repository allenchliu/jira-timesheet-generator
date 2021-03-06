package com.jira.timesheet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.DefaultHttpClient;

import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.Issue.SearchResult;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.WorkLog;

public class TimesheetGenerator {
    /**
     * Starting point of the application.
     * 
     * @param args
     * @throws Exception
     *             In case something went terribly wrong.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java -jar timesheet-generator.jar yourJiraUserName yourJiraPassword exportedfFlePath startDate users endDate");
            return;
        }

        String username = args[0];
        String password = args[1];
        String filePath = args[2];
        String startDate = args[3].contains("\"") ? args[3] : "\"" + args[3] + "\"";
        String users = args.length > 4 ? args[4] : "all";
        String endDate = args.length > 5 ? (args[5].contains("\"") ? args[5] : "\"" + args[5] + "\"") : "now()";

        try {
            JiraClient jira = prepareJiraClient(username, password);
            String timesheet = parseTimesheet(jira, users, startDate, endDate);
            System.out.println();
            System.out.println("Saving to CSV...");
            saveStringAsFile(timesheet, filePath);
            // System.out.println();
            System.out.println("TIMESHEET GENERATED SUCCESSFULLY");
        }
        catch (JiraException ex) {
            System.err.println(ex.getMessage());

            if (ex.getCause() != null)
                System.err.println(ex.getCause().getMessage());
        }
        catch (IOException ex) {
            System.err.println(ex.getMessage());

            if (ex.getCause() != null)
                System.err.println(ex.getCause().getMessage());
        }
    }

    private static String parseTimesheet(JiraClient jira, String username, String startDate, String endDate) throws JiraException, ParseException {
        Date start = parseDate(startDate);
        Date end = new Date();
        String jql = "worklogDate >= " + startDate;
        if (!endDate.equalsIgnoreCase("now()")) {
            jql += " and worklogDate <= " + endDate;
            end = parseDate(endDate);
        }
        if (!username.equalsIgnoreCase("all")) {
            jql += " and (worklogAuthor  in ( " + username + " ))";
        }
        System.out.println("Searching for issues by JQL: " + jql + "...");
        // SearchResult result = jira.searchIssues(jql, countLoggedWork ? "*all,-comment" : "summary", "changelog", 1000, 0);
        SearchResult result = jira.searchIssues(jql, "project,issuetype,summary", null, 10000, 0);
        StringBuilder issues = filterResults(username, start, end, result);
        return issues.toString();
    }

    private static StringBuilder filterResults(String username, Date start, Date end, SearchResult result) throws JiraException {
        System.out.println("Parsing " + result.issues.size() + " issues");
        StringBuilder issues = new StringBuilder();
        issues.append("Project\tType\tKey\tTitle\tUsername\tTime Spent\tDate\n");
        for (Issue issue : result.issues) {
            for (WorkLog workLog : issue.getAllWorkLogs()) {
                if (username.equalsIgnoreCase("all") || (username.toLowerCase().contains(workLog.getAuthor().getName().toLowerCase()))) {
                    if (workLog.getCreatedDate().compareTo(start) >= 0 && workLog.getCreatedDate().before(end)) {
                        // System.out.println(issue);
                        issues.append(issue.getProject().getName()).append("\t");
                        issues.append(issue.getIssueType().getName()).append("\t");
                        issues.append(issue).append("\t");
                        issues.append(issue.getSummary()).append("\t");
                        issues.append(workLog.getAuthor()).append("\t");
                        issues.append(toHours(workLog.getTimeSpent())).append("\t");
                        issues.append(new SimpleDateFormat("MM/dd/yyyy").format(workLog.getCreatedDate())).append("\n");
                    }
                }
            }
        }
        return issues;
    }

    private static Date parseDate(String str) throws ParseException {
        DateFormat format;
        str = str.replaceAll("\"", "");
        if (str.contains("/")) {
            format = new SimpleDateFormat("yyyy/MM/dd");
        }
        else {
            format = new SimpleDateFormat("yyyy-MM-dd");
        }
        return format.parse(str);
    }

    private static String toHours(String time) {
        double hours = 0;
        String tmp = time;
        int indexOfw = time.indexOf("w");
        if (indexOfw >= 0) {
            tmp = time.substring(0, indexOfw);
            time = time.substring(indexOfw + 1).trim();
            hours += Integer.parseInt(tmp) * 40;
        }
        int indexOfd = time.indexOf("d");
        if (indexOfd >= 0) {
            tmp = time.substring(0, indexOfd);
            time = time.substring(indexOfd + 1).trim();
            hours += Integer.parseInt(tmp) * 8;
        }
        int indexOfh = time.indexOf("h");
        if (indexOfh >= 0) {
            tmp = time.substring(0, indexOfh);
            time = time.substring(indexOfh + 1).trim();
            hours += Integer.parseInt(tmp);
        }
        int indexOfm = time.indexOf("m");
        if (indexOfm >= 0) {
            tmp = time.substring(0, indexOfm);
            hours += Integer.parseInt(tmp) / 60.0;
        }
        return hours + "";
    }

    /**
     * Prepares JIRA REST API client. BEWARE: Bypasses SSL certificate verification, trusts even fake jira.abank.cz (boo hoo).
     * 
     * @param username
     *            Username used for login.
     * @param password
     *            Password used for login.
     * @return JIRA REST API client.
     * @throws Exception
     *             In case anything went wrong.
     */
    private static JiraClient prepareJiraClient(String username, String password) throws Exception {
        BasicCredentials creds = new BasicCredentials(username, password);
        JiraClient jira1 = new JiraClient("https://motionglobal.atlassian.net/", creds);
        Field field = jira1.getClass().getDeclaredField("restclient");
        field.setAccessible(true);
        Object restClient = field.get(jira1);
        Field declaredField = restClient.getClass().getDeclaredField("httpClient");
        declaredField.setAccessible(true);
        DefaultHttpClient httpClient = (DefaultHttpClient) declaredField.get(restClient);
        org.apache.http.conn.ssl.SSLSocketFactory sslsf = new org.apache.http.conn.ssl.SSLSocketFactory(new TrustStrategy() {
            public boolean isTrusted(final X509Certificate[] chain, String authType) throws CertificateException {
                return true;
            }

        });
        httpClient.getConnectionManager().getSchemeRegistry().register(new Scheme("https", 443, sslsf));
        JiraClient jira = jira1;
        return jira;
    }

    private static void saveStringAsFile(String content, String filePath) throws IOException {
        File file = new File(filePath);
        System.out.println("Writing as file: " + file.getAbsolutePath());
        if (file.exists()) {
            file.delete();
        }
        FileWriter writer = new FileWriter(file);
        BufferedWriter out = new BufferedWriter(writer);
        writer.write(content);
        // System.out.println(content);
        writer.flush();
        writer.close();
    }
}

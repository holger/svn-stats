package com.oneandone.sales.svnstats;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SvnStats {
    private SVNRepository repository;
    private int numberOfChangedFiles;
    private Map<String, FileStats> changedFiles = new HashMap<String, FileStats>();

    public SvnStats(String path) throws SVNException {
        setupLibrary();
        repository = SVNRepositoryFactory.create(SVNURL.parseURIEncoded(path));
    }

    public void login(String username, String password) {
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(username, password);
        repository.setAuthenticationManager(authManager);
    }

    public void analyseChanges(String start, String end) throws SVNException {
        Collection logEntries = fetchLogEntries(start, end);

        for (Iterator entries = logEntries.iterator(); entries.hasNext(); ) {
            SVNLogEntry logEntry = (SVNLogEntry) entries.next();

            if (logEntry.getChangedPaths().size() > 0) {
                Set changedPathsSet = logEntry.getChangedPaths().keySet();

                for (Iterator changedPaths = changedPathsSet.iterator(); changedPaths.hasNext(); ) {
                    SVNLogEntryPath entryPath = (SVNLogEntryPath) logEntry.getChangedPaths().get(changedPaths.next());
                    changedPath(entryPath);
                }
            }
        }
    }

    private Collection fetchLogEntries(String start, String end) throws SVNException {
        long startRevision = parseRevision(start, 0);
        long endRevision = parseRevision(end, repository.getLatestRevision());

        return this.fetchLogEntries(startRevision, endRevision);
    }

    private long parseRevision(String revisionString, long fallback) throws SVNException {
        if (revisionString == null) {
            return fallback;
        }

        long revision;

        if (revisionString.startsWith("r")) {
            revision = Long.parseLong(revisionString.substring(2));
        } else if (revisionString.startsWith("-")) {
            Calendar cal = Calendar.getInstance();
            int beforeMonths = Integer.parseInt(revisionString.substring(2));
            cal.add(Calendar.MONTH, -beforeMonths);
            revision = repository.getDatedRevision(cal.getTime());
        } else {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            try {
                Date date = formatter.parse(revisionString);
                revision = repository.getDatedRevision(date);
            } catch (ParseException e) {
                revision = fallback;
            }
        }

        return revision;
    }

    private Collection fetchLogEntries(long startRevision, long endRevision) {
        Collection logEntries = null;
        try {
            logEntries = repository.log(new String[]{""}, null, startRevision, endRevision, true, true);
        } catch (SVNException svne) {
            System.err.println("error while fetching the repository revision: " + svne.getMessage());
            System.exit(1);
        }
        return logEntries;
    }

    private void changedPath(SVNLogEntryPath path) {
        if (isFile(path.getPath())) {
            if (changedFiles.containsKey(path.getPath())) {
                changedFiles.get(path.getPath()).update(path);
            } else {
                changedFiles.put(path.getPath(), new FileStats(path));
            }
        }
    }

    private boolean isFile(String path) {
        return path.contains(".");
    }

    public String toString() {
        Map<String, FileTypeStats> types = new HashMap<String, FileTypeStats>();
        for (FileStats file : changedFiles.values()) {
            FileTypeStats type;
            if (types.containsKey(file.getType())) {
                type = types.get(file.getType());
            } else {
                type = new FileTypeStats(file.getType());
                types.put(type.getType(), type);
            }
            type.addFile(file);
        }

        StringBuilder result = new StringBuilder();
        result.append("Changed Files: " + changedFiles.size() + "\n");
        result.append("\n");
        for (FileTypeStats type : types.values()) {
            result.append(type.getType() + "\n");
            result.append("   Files: " + type.getFiles().size() + "\n");
            result.append("   Changes: " + type.getTotalChanges() + "\n");
            result.append("   Added: " + type.getAdded() + "\n");
            result.append("   Modified: " + type.getModified() + "\n");
            result.append("   Deleted: " + type.getDeleted() + "\n");
            result.append("\n");
            if (numberOfChangedFiles > 0) {
                result.append("   Modified Files:\n");
                int fileCount = 0;
                for (FileStats file : type.files()) {
                    if (fileCount >= numberOfChangedFiles) {
                        break;
                    }
                    fileCount += 1;
                    result.append("      " + file.getTotalChanges() + " - " + file.getName() + "\n");
                }
                result.append("\n");
            }

        }
        return result.toString();
    }

    public void numberOfChangedFiles(int i) {
        numberOfChangedFiles = i;
    }

    private void setupLibrary() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }
}

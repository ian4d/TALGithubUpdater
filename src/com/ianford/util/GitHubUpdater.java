package com.ianford.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 * Scans a local directory for files, sorts them by name, and attempts to add the last file in the list to a Github repository
 */
public class GitHubUpdater {

	/**
	 * Main Method
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		Options options = new Options();

		Option argRoot = new Option("r", "root", true, "The root of the file system to process from");
		argRoot.setRequired(true);
		options.addOption(argRoot);

		Option argTarget = new Option("t", "target", true, "The target folder to upload new files from");
		argTarget.setRequired(true);
		options.addOption(argTarget);
		
		Option argRepo = new Option("repo", "repository", true, "The repistory to operate on");
		argRepo.setRequired(true);
		options.addOption(argRepo);
		
		Option argUsername = new Option("u", "username", true, "The username of the user");
		argUsername.setRequired(true);
		options.addOption(argUsername);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException ex) {
			System.out.println(ex.getMessage());
			formatter.printHelp("TAL Updater", options);
			System.exit(1);
			return;
		}

		String rootFilePath = cmd.getOptionValue("root");
		String targetFilePath = cmd.getOptionValue("target");
		String repositoryName = cmd.getOptionValue("repository");
		String username = cmd.getOptionValue("username");

		System.out.println(String.format("Root Path: %s", rootFilePath));
		System.out.println(String.format("Target Path: %s", targetFilePath));

		GitHubUpdater updater = new GitHubUpdater(rootFilePath, targetFilePath, repositoryName, username);
		updater.update();
	}

	// Local variables
	private final String rootPath;
	private final String targetPath;
	private final String repositoryName;
	private final String username;
	
	/**
	 * Constructor
	 * 
	 * @param rootPath
	 * @param targetPath
	 */
	public GitHubUpdater(String rootPath, String targetPath, String repositoryName, String username) {
		this.rootPath = rootPath;
		this.targetPath = targetPath;
		this.repositoryName = repositoryName;
		this.username = username;
	}

	/**
	 * Acquires a file, throws an exception if needed
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private File acquireFile(String path) throws IOException, Exception {
		File file = new File(path);
		if (!file.exists()) {
			throw new Exception(String.format("File does not exist: %s", file.getCanonicalPath()));
		}
		if (!file.isDirectory()) {
			throw new Exception(String.format("File is not directory: %s", file.getCanonicalPath()));
		}
		return file;
	}

	/**
	 * Acquires a list of episode files using a FilenameFilter
	 * 
	 * @param targetFile
	 * @return
	 */
	private List<File> acquireEpisodeFileList(File targetFile) {
		return Arrays.asList(targetFile.listFiles(new FilenameFilter() {

			private Pattern pattern = Pattern.compile("episode\\-\\d+\\.csv");

			@Override
			public boolean accept(File dir, String name) {
				return pattern.matcher(name).matches();
			}
		}));
	}

	private void update() throws Exception {

		// Connect to Github
		GitHub github = GitHub.connect();

		// Find Repository
		GHRepository repo = getRepo(github, username, repositoryName);
		if (null == repo) {
			return;
		}

		// Validate root path
		File fileRoot = acquireFile(rootPath);

		// Validate target path relative to root
		File fileTarget = acquireFile(String.format("%s%s%s", fileRoot.getCanonicalPath(), File.separator, targetPath));
		System.out.println(String.format("fileTarget: %s", fileTarget.toString()));
		
		// Get List of all files
		List<File> fileList = acquireEpisodeFileList(fileTarget);
		Collections.sort(fileList);
		
		// Output file count
		System.out.println(String.format("File Count: %d", fileList.size()));
		
		// For each file in the list
		for (File file : fileList) {

			// Get the relative path and print it
			String relativePath = fileRoot.toURI().relativize(file.toURI()).toString();
			System.out.println(String.format("File path: %s", relativePath));

			// Read all of the lines into a string
			BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
			List<String> lines = bufferedReader.lines().collect(Collectors.toList());

			// Attempt to add the new file if it's not already present
			String outputMsg = null;
			try {
				GHContent content = repo.getFileContent(relativePath);
				if (content != null && content.isFile()) {
					outputMsg = String.format("Skipping %s because it is already present", relativePath);
				}
			} catch (Exception ex) {
				outputMsg = String.format("Adding %s", relativePath);
				repo.createContent(String.join("\n", lines), outputMsg, relativePath);
			}
			System.out.println(outputMsg);
		}
	}

	/**
	 * Get a reference to the github repo or create it if it doesn't exist
	 * 
	 * @param github
	 * @param user
	 * @param repoName
	 * @return
	 */
	private GHRepository getRepo(GitHub github, String user, String repoName) {
		GHRepository repo = null;
		try {
			return github.getRepository(String.format("%s/%s", user, repoName));
		} catch (IOException e) {
			System.out.println("Repo not found, creating now");
			return createRepo(github, repoName);
		}
	}

	/**
	 * Create a github repo
	 * 
	 * @param github
	 * @param repoName
	 * @return
	 */
	private GHRepository createRepo(GitHub github, String repoName) {
		try {
			return github.createRepository(repoName).create();
		} catch (IOException e) {
			System.out.println("Repo creation failed");
			return null;
		}
	}

}

package de.uni.hannover.studip.sync.models;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni.hannover.studip.sync.datamodel.*;
import de.uni.hannover.studip.sync.exceptions.*;

/**
 * Semester/Course/Folder/Document tree sync.
 * 
 * @author Lennart Glauer
 */
public class TreeSync {
	
	/**
	 * The sync root directory.
	 */
	private final File rootDirectory;
	
	/**
	 * Thread pool.
	 */
	private final ExecutorService threadPool;

	public TreeSync(File rootDirectory) {
		if (!rootDirectory.isDirectory()) {
			throw new IllegalStateException("Root directory does not exist!");
		}
		
		this.rootDirectory = rootDirectory;
		
		threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}
	
	/**
	 * Shutdown thread pool.
	 */
	public void shutdown() {
		threadPool.shutdown();
	}
	
	/**
	 * Synchronize all documents.
	 * 
	 * @param tree
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	public synchronized int sync(File tree, boolean doAllSemesters) throws JsonParseException, JsonMappingException, IOException {
		/* Read existing tree. */
		ObjectMapper mapper = new ObjectMapper();
		SemestersTreeNode rootNode = mapper.readValue(tree, SemestersTreeNode.class);
		
		/* A phaser is actually a up and down latch, it's used to wait until all jobs are done. */
		Phaser phaser = new Phaser(1); /* = self. */
		
		/* Current unix timestamp. */
		long now = System.currentTimeMillis() / 1000L;
		
		/* Update tree with multiple threads. */
		for (SemesterTreeNode semester : rootNode.semesters) {
			// If doAllSemesters is false we will only update the current semester.
			if (doAllSemesters || (now > semester.begin && now < semester.end)) {
				File semesterDirectory = new File(rootDirectory, removeIllegalCharacters(semester.title));
				
				if (!semesterDirectory.exists() && !semesterDirectory.mkdir()) {
					throw new IllegalStateException("Could not create semester directory!");
				}
				
				for (CourseTreeNode course : semester.courses) {
					File courseDirectory = new File(semesterDirectory, removeIllegalCharacters(course.title));
					
					if (!courseDirectory.exists() && !courseDirectory.mkdir()) {
						throw new IllegalStateException("Could not create course directory!");
					}
					
					doFolder(phaser, course.root, courseDirectory);
				}
			}
		}
		
		/* Wait until all jobs are done. */
		phaser.arriveAndAwaitAdvance();
		
		System.out.println("Sync done!");
		
		return phaser.getRegisteredParties() - 1;
	}
	
	/**
	 * Remove/replace illegal chars from path/file name.
	 * 
	 * @param file
	 * @return
	 */
	private String removeIllegalCharacters(String file) {
		/* Replace separators. */
		file = file.replaceAll("[\\/]+", "-");
		/* Remove other illegal chars. */
		return file.replaceAll("[<>:\"|?*]+", "");
	}
	
	/**
	 * Folder node handler.
	 * 
	 * @param phaser
	 * @param folderNode The folder node
	 * @param parentDirectory The parent directory
	 */
	private void doFolder(Phaser phaser, DocumentFolderTreeNode folderNode, File parentDirectory) {
		/* Traverse folder structure (recursive). */
		for (DocumentFolderTreeNode folder : folderNode.folders) {
			File folderDirectory = new File(parentDirectory, removeIllegalCharacters(folder.name));
			
			if (!folderDirectory.exists() && !folderDirectory.mkdir()) {
				throw new IllegalStateException("Could not create course directory!");
			}
			
			doFolder(phaser, folder, folderDirectory);
		}

		for (DocumentTreeNode document : folderNode.documents) {
			doDocument(phaser, document, parentDirectory);
		}
	}
	
	/**
	 * Document node handler.
	 * 
	 * @param phaser
	 * @param documentNode The document node
	 * @param parentDirectory The parent directory
	 */
	private void doDocument(Phaser phaser, DocumentTreeNode documentNode, File parentDirectory) {
		File documentFile = new File(parentDirectory, removeIllegalCharacters(documentNode.filename));
		
		if (documentFile.exists()) {
			if (documentFile.length() != documentNode.filesize || documentFile.lastModified() < documentNode.chdate) {
				phaser.register();
				
				if (Config.getInstance().getRenameModifiedFiles()) {
					File rename;
					int i = 1;
					
					// Append file version number.
					do {
						rename = new File(documentFile.getAbsolutePath() + "_" + i++);
					} while(rename.exists());

					documentFile.renameTo(rename);
				}

				/* Download modified file. */
				threadPool.execute(new DownloadDocumentJob(phaser, documentNode, documentFile));

				System.out.println("Modified: " + documentNode.name);
			}
		
		} else {
			phaser.register();
			
			/* Download new file. */
			threadPool.execute(new DownloadDocumentJob(phaser, documentNode, documentFile));

			System.out.println("New: " + documentNode.name);
		}
	}
	
	/**
	 * Download document job.
	 * 
	 * @author Lennart Glauer
	 */
	private class DownloadDocumentJob implements Runnable {
		
		/**
		 * Phaser.
		 */
		private final Phaser phaser;
		
		/**
		 * Document node.
		 * The document node to download.
		 */
		private final DocumentTreeNode documentNode;
		
		/**
		 * Document file.
		 * The file location to store the document.
		 */
		private final File documentFile;
		
		/**
		 * Download document job.
		 * 
		 * @param phaser
		 * @param documentNode The document node to download
		 * @param documentFile The file location to store the document
		 */
		public DownloadDocumentJob(Phaser phaser, DocumentTreeNode documentNode, File documentFile) {
			this.phaser = phaser;
			this.documentNode = documentNode;
			this.documentFile = documentFile;
		}

		@Override
		public void run() {
			try {
				RestApi.downloadDocumentById(documentNode.document_id, documentFile);

				System.out.println(documentNode.name + " downloaded");
				
			} catch (UnauthorizedException e) {
				e.printStackTrace();
			} catch (ForbiddenException e) {
				e.printStackTrace();
			} catch (NotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				/* Job done. */
				phaser.arrive();
			}
		}
		
	}
}

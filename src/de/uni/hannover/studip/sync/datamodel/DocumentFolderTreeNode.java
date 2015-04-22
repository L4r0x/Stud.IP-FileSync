package de.uni.hannover.studip.sync.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.elanev.studip.android.app.backend.datamodel.DocumentFolder;

/**
 * Folder tree node used for json object binding.
 * 
 * @author Lennart Glauer
 * @notice Thread safe
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentFolderTreeNode {

	public String folder_id;
	public String user_id;
	public String name;
	public String mkdate;
	public Long chdate;
	
	/* Child nodes. */
	public List<DocumentFolderTreeNode> folders = Collections.synchronizedList(new ArrayList<DocumentFolderTreeNode>());
	public List<DocumentTreeNode> documents = Collections.synchronizedList(new ArrayList<DocumentTreeNode>());
	
	public DocumentFolderTreeNode() {
	}
	
	public DocumentFolderTreeNode(DocumentFolder folder) {
		this.folder_id = folder.folder_id;
		this.user_id = folder.user_id;
		this.name = folder.name;
		this.mkdate = folder.mkdate;
		this.chdate = folder.chdate;
	}
	
}
package de.uni.hannover.studip.sync.views;

import java.io.File;

import javax.swing.JOptionPane;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.uni.hannover.studip.sync.Main;
import de.uni.hannover.studip.sync.exceptions.NotFoundException;
import de.uni.hannover.studip.sync.exceptions.UnauthorizedException;
import de.uni.hannover.studip.sync.models.Config;
import de.uni.hannover.studip.sync.models.OAuth;
import de.uni.hannover.studip.sync.models.RestApi;
import de.uni.hannover.studip.sync.models.TreeSync;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;

public class OverviewController extends AbstractController {

	@FXML
	private ProgressIndicator progress;

	@FXML
	private Button syncButton;

	@FXML
	public void handleSync() {
		syncButton.setDisable(true);
		syncButton.setText("Updating...");

		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					OAuth oauth = OAuth.getInstance();
					oauth.restoreAccessToken();

					// Test access token.
					RestApi.getUserById(null);

					String rootDir = Config.getInstance().getRootDirectory();
					if (rootDir != null) {
						try (TreeSync tree = new TreeSync(new File(rootDir))) {
							File treeFile = Config.getInstance().openTreeFile();

							tree.setProgress(progress);

							// Update documents.
							try {
								tree.update(treeFile, false);

							} catch (JsonParseException | JsonMappingException e) {
								// Invalid tree file, lets build a new one.
								tree.build(treeFile);
							}

							// Update sync button.
							Platform.runLater(new Runnable() {

								@Override
								public void run() {
									syncButton.setText("Downloading...");
								}

							});

							// Download documents.
							tree.sync(treeFile, false);
						}

					}

				} catch (UnauthorizedException | NotFoundException e) {
					OAuth.getInstance().removeAccessToken();

					Platform.runLater(new Runnable() {

						@Override
						public void run() {
							// Redirect to login.
							getMain().setView(Main.OAUTH);
						}

					});

				} catch (Exception e) {
					JOptionPane.showMessageDialog(null, e.getMessage(), "Fehler", JOptionPane.ERROR_MESSAGE);
				}

				// Update progress and sync button.
				Platform.runLater(new Runnable() {

					@Override
					public void run() {
						progress.setProgress(1);
						syncButton.setText("Sync");
						syncButton.setDisable(false);
					}

				});
			}

		}).start();
	}

}
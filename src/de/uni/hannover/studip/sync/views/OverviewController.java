package de.uni.hannover.studip.sync.views;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.uni.hannover.studip.sync.Main;
import de.uni.hannover.studip.sync.models.Config;
import de.uni.hannover.studip.sync.models.OAuth;
import de.uni.hannover.studip.sync.models.TreeSync;
import de.uni.hannover.studip.sync.utils.SimpleAlert;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;

/**
 * 
 * @author Lennart Glauer
 *
 */
public class OverviewController extends AbstractController {

	private static final Logger LOG = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
	private static final Config CONFIG = Config.getInstance();
	private static final OAuth OAUTH = OAuth.getInstance();

	@FXML
	protected ProgressIndicator progress;

	@FXML
	protected Label progressLabel;

	@FXML
	protected Button syncButton;

	@FXML
	public void handleSync() {
		(new Thread(() -> {
			if (!OAUTH.restoreAccessToken()) {
				OAUTH.removeAccessToken();
				Platform.runLater(() -> getMain().setView(Main.OAUTH));
				return;
			}

			final String rootDir = CONFIG.getRootDirectory();
			if (rootDir == null || rootDir.isEmpty()) {
				SimpleAlert.error("Kein Ziel Ordner gewählt.");
				return;
			}

			if (!Main.TREE_LOCK.tryLock()) {
				return;
			}

			try (final TreeSync tree = new TreeSync(Paths.get(rootDir))) {
				Platform.runLater(() -> {
					getMain().getRootLayoutController().getMenu().setDisable(true);
					syncButton.setDisable(true);
					syncButton.setText("Updating...");
				});

				final Path treeFile = Config.openTreeFile();
				int numberOfRequests;

				tree.setProgress(progress, progressLabel);

				/* Update documents. */
				try {
					numberOfRequests = tree.update(treeFile);

				} catch (NoSuchFileException | JsonParseException | JsonMappingException e) {
					/* Invalid tree file. */
					numberOfRequests = tree.build(treeFile);
				}

				Platform.runLater(() -> {
					progressLabel.setText("");
					syncButton.setText("Downloading...");
				});

				/* Download documents. */
				numberOfRequests += tree.sync(treeFile, CONFIG.isDownloadAllSemesters());

				if (LOG.isLoggable(Level.INFO)) {
					LOG.info("Number of requests: " + numberOfRequests);
				}

			} catch (IOException e) {
				Platform.runLater(() -> SimpleAlert.exception(e));

			} finally {
				Platform.runLater(() -> {
					progressLabel.setText("");
					syncButton.setText("Sync");
					syncButton.setDisable(false);
					getMain().getRootLayoutController().getMenu().setDisable(false);
				});

				Main.TREE_LOCK.unlock();
			}
		})).start();
	}
}
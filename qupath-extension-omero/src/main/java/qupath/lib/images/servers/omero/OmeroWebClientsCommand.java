package qupath.lib.images.servers.omero;

import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.PaneTools;

/**
 * Command to manually manage OMERO web clients. This offers the possibility to log in/off
 * and 'forget' OMERO web clients.
 * 
 * @author Melvin Gelbard
 */
public class OmeroWebClientsCommand implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(OmeroWebClientsCommand.class);
	
	private QuPathGUI qupath;
	private Stage dialog;
	private ExecutorService executor;
	private ObservableSet<ServerInfo> clientsDisplayed;
	
	// GUI
	private GridPane mainPane;
	
	OmeroWebClientsCommand(QuPathGUI qupath) {
		this.qupath = qupath;
		this.clientsDisplayed = FXCollections.observableSet();
	}

	public void run() {
		if (dialog == null) {
			// Get connection status of each imageServer in separate thread
			executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("OMERO-server-status", true));
			
			dialog = new Stage();
			mainPane = new GridPane();
			mainPane.setMinWidth(250);
			mainPane.setMinHeight(50);
			mainPane.setPadding(new Insets(0.0, 0.5, 5, 0.5));
			for (var client: OmeroWebClients.getAllClients()) {
				var serverInfo = new ServerInfo(qupath, client);
				clientsDisplayed.add(serverInfo);
			}
			
			
			// If empty, display 'No OMERO clients' label
			if (clientsDisplayed.isEmpty()) {
				Platform.runLater(() -> {
					mainPane.setAlignment(Pos.CENTER);
					mainPane.add(new Label("No OMERO clients"), 0, 0);			        						
				});
			}
			
			// If a change is detected in the clients list, refresh pane
			OmeroWebClients.getAllClients().addListener(new ListChangeListener<OmeroWebClient>() {
			    @Override
			    public void onChanged(Change<? extends OmeroWebClient> c) {
			        refreshServerGrid();
			        dialog.getScene().getWindow().sizeToScene();
			    }
			});
			
			// If project has no OMERO server, display label
			clientsDisplayed.addListener(new SetChangeListener<ServerInfo>() {
			    @Override
			    public void onChanged(Change<? extends ServerInfo> c) {
			        if (clientsDisplayed.isEmpty()) {
			        	Platform.runLater(() -> {
			        		mainPane.setAlignment(Pos.CENTER);
			        		mainPane.add(new Label("No OMERO clients"), 0, 0);			        		
			        	});
			        }
			    }
			});
			
			refreshServerGrid();
			
			mainPane.setVgap(10.0);
			dialog.sizeToScene();
			dialog.setResizable(false);
			dialog.setTitle("OMERO web clients");
			dialog.setScene(new Scene(mainPane));
			dialog.setOnCloseRequest(e -> dialog = null);
			QuPathGUI qupath = QuPathGUI.getInstance();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
		} else
			dialog.requestFocus();

		dialog.sizeToScene();
		dialog.show();
	}
	
	
	private void refreshServerGrid() {
		mainPane.getChildren().clear();
		var allClients = OmeroWebClients.getAllClients();
		for (var client: allClients) {
			// If new client is not displayed, add it to the set
			if (clientsDisplayed.stream().noneMatch(e -> e.client.equals(client)))
				clientsDisplayed.add(new ServerInfo(qupath, client));
		}
		
		int row = 0;
		// Using iterator to avoid ConcurrentModificationExceptions
		for (var i = clientsDisplayed.iterator(); i.hasNext();) {
			var serverInfo = i.next();
			// If the client list does not contain this client, remove from set
			if (!allClients.contains(serverInfo.client)) {
				i.remove();
				continue;
			}
			mainPane.addRow(row++, serverInfo.getPane());
			if (dialog != null)
				dialog.sizeToScene();
		}
	}
	
	
	/**
	 * Class to keep info about an OMERO server for display.
	 * The point here is to keep track of the bindings with {@code OmeroWebClients} 
	 * and to avoid having to recreate panes after each update.
	 * <p>
	 * Each instance has a {@code GridPane} which is created <b>once</b>. Within this pane, the 
	 * {@code titledPane} is continuously updated according to the URI list of the 
	 * {@code client} (each addition/removal recreate the title pane content).
	 *
	 */
	class ServerInfo {

		private QuPathGUI qupath;
		private OmeroWebClient client;
		private GridPane pane;
		
		private IntegerProperty nImages;

		private ServerInfo(QuPathGUI qupath, OmeroWebClient client) {
			this.qupath = qupath;
			this.client = client;
			this.nImages = new SimpleIntegerProperty(0);
			this.pane = createServerPane();			
		}
		
		private GridPane getPane() {
			return pane;
		}
		
		private GridPane createServerPane() {
			// The username should be the same for all images in the server
			String username = client.getUsername();
			GridPane gridPane = new GridPane();
			BorderPane infoPane = new BorderPane();
			GridPane actionPane = new GridPane();
		
			URI uri = client.getServerURI();
			Label userLabel = new Label();
			userLabel.textProperty().bind(Bindings
					.when(client.usernameProperty().isNotEmpty())
					.then(Bindings.concat(uri.toString(), " (", client.usernameProperty(), ")"))
					.otherwise(Bindings.concat(uri.toString())));
			
			// Bind state node
			userLabel.graphicProperty().bind(Bindings.createObjectBinding(() -> {
				if (client.getUsername().isEmpty())
					return OmeroTools.createStateNode(client.checkIfLoggedIn());
				else
					return OmeroTools.createStateNode(client.logProperty().get());
			}, client.usernameProperty()));
			
			// Make it appear on the right of the server's URI
			userLabel.setContentDisplay(ContentDisplay.RIGHT);
			
			nImages.bind(Bindings.size(client.getURIs()));
			
			TitledPane tp = new TitledPane();
			tp.textProperty().bind(Bindings.concat(nImages, " image(s)"));
			tp.setMaxWidth(Double.MAX_VALUE);
			tp.setExpanded(false);
			tp.heightProperty().addListener((v, o, n) -> Platform.runLater(() -> dialog.sizeToScene()));
			tp.widthProperty().addListener((v, o, n) -> Platform.runLater(() -> dialog.sizeToScene()));
			
			// If the login status or the client's username has changed or a new image is opened, recreate the titlePane content
			tp.contentProperty().bind(Bindings.createObjectBinding(() -> createImagesPane(client), client.usernameProperty(), client.getURIs()));
			tp.collapsibleProperty().bind(nImages.greaterThan(0));
			
			Platform.runLater(() -> {
				try {
					// These 2 next lines help prevent NPE
					tp.applyCss();
					tp.layout();
					if (!tp.getStyle().isEmpty()) {
						tp.lookup(".title").setStyle("-fx-background-color: transparent");
						tp.lookup(".title").setEffect(null);						
						tp.lookup(".content").setStyle("-fx-border-color: null");						
					}
				} catch (Exception e) {
					logger.error("Error setting CSS style: {}", e.getLocalizedMessage());
				}
			});
			infoPane.setBottom(tp);
			
			Button connectionBtn = new Button();
			// Bind button's text properly
			connectionBtn.textProperty().bind(Bindings.createStringBinding(() -> {
				if (client.isLoggedIn()) {
					if (client.getUsername().isEmpty())
						return "Log in";
					return "Log out";
				}
				return "Log in";
			},  client.logProperty(), client.usernameProperty()));
			
			Button removeBtn = new Button("Remove");
			PaneTools.addGridRow(actionPane, 0, 0, null, connectionBtn, removeBtn);
			infoPane.setLeft(userLabel);
			infoPane.setRight(actionPane);
			
			connectionBtn.setOnAction(e -> {
				if (connectionBtn.getText().equals("Log in")) {
					boolean success = true;
					success = client.logIn();
					if (!success)
						Dialogs.showErrorMessage("Log in to OMERO server", "Could not log in to server with credentials. Check the log for more info.");
				} else {
					// Check again the state, in case it wasn't refreshed in time
					if (client.isLoggedIn())
						client.logOut();
				}
			});
			
			removeBtn.setOnMouseClicked(e -> {
				// Check if the webclient to delete is currently used in any viewer
				if (qupath.getViewers().stream().anyMatch(viewer -> {
					if (viewer.getServer() == null)
						return false;
					URI viewerURI = viewer.getServer().getURIs().iterator().next();
					return client.getURIs().contains(viewerURI);
				})) {
					Dialogs.showMessageDialog("Remove OMERO client", "You need to close images from this server in the viewer first!");
					return;
				}
				if (!username.isEmpty() && client.isLoggedIn())
					client.logOut();
				OmeroWebClients.removeClient(client);
			});
			removeBtn.disableProperty().bind(client.logProperty().and(client.usernameProperty().isNotEmpty()));
			

			
			PaneTools.addGridRow(gridPane, 0, 0, null, infoPane);
			PaneTools.addGridRow(gridPane, 1, 0, null, tp);
			
			GridPane.setHgrow(gridPane, Priority.ALWAYS);
			GridPane.setHgrow(tp, Priority.ALWAYS);
			actionPane.setHgap(5.0);
			gridPane.setPadding(new Insets(5, 5, 5, 5));
			
			gridPane.setStyle("-fx-border-color: black;");
			return gridPane;
		}
		
		
		private GridPane createImagesPane(OmeroWebClient client) {
			var imageList = qupath.getProject().getImageList();
			
			GridPane gp = new GridPane();
			for (URI imageUri: client.getURIs()) {
				// Check if client's servers haven't been deleted from project
				var anyMatch = imageList.parallelStream().anyMatch(e -> {
					try {
						return e.getServerURIs().iterator().next().equals(imageUri);
					} catch (Exception ex) {
						logger.warn(ex.getLocalizedMessage());
					}
					return false;
				});
				
				if (anyMatch) {
					// To save time, check the imageServers' status in other threads and update the pane later
					ProgressIndicator pi = new ProgressIndicator();
					pi.setPrefSize(15, 15);
					Label imageServerName = new Label("../" + imageUri.getQuery(), pi);
					imageServerName.setContentDisplay(ContentDisplay.RIGHT);
					PaneTools.addGridRow(gp, gp.getRowCount(), 0, null, imageServerName);
					
					executor.submit(() -> {
						try {
							final boolean canAccessImage = client.canAccessImage(imageUri);
							String tooltip = (client.isLoggedIn() && !canAccessImage) ? "Unreachable image (access not permitted)" : imageUri.toString();
							Platform.runLater(() -> {
								imageServerName.setTooltip(new Tooltip(tooltip));
								imageServerName.setGraphic(OmeroTools.createStateNode(canAccessImage));									
							});
						} catch (ConnectException ex) {
							Platform.runLater(() -> {
								imageServerName.setTooltip(new Tooltip("Unreachable image"));									
								imageServerName.setGraphic(OmeroTools.createStateNode(false));
							});
						}
					});				
				}
			}
			gp.setHgap(5.0);
			return gp;
		}
	}
}

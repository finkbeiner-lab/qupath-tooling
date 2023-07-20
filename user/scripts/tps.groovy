import groovy.transform.InheritConstructors

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.Number;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableStringValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback
import javafx.util.StringConverter

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.ext.imagecombinerwarpy.gui.ImageCombinerWarpyServerOverlay;
import qupath.ext.imagecombinerwarpy.gui.ServerOverlay;
import qupath.ext.imagecombinerwarpy.gui.TPSTransform;
import qupath.ext.imagecombinerwarpy.gui.TPSTransformServer;
import qupath.ext.imagecombinerwarpy.gui.AffineTransformServer;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.images.stores.DefaultImageRegionStore;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.images.stores.ImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.servers.AbstractImageServer
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.io.GsonTools;
import qupath.lib.io.ROITypeAdapters
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathObjects
import qupath.lib.objects.hierarchy.PathObjectHierarchy
import qupath.lib.projects.Project
import qupath.lib.projects.ProjectImageEntry
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.ROIs
import qupath.lib.roi.interfaces.ROI
import qupath.lib.roi.LineROI
import qupath.lib.roi.PointsROI
import qupath.lib.roi.PolygonROI



class PointUtils {
    static def pointsToArray(List points) {
        assert points.findAll({it.size() == 2}).size() == points.size()
        def pointsArray = new double[2][points.size()]
        points.withIndex().each({point, idx ->
            pointsArray[0][idx] = point[0]
            pointsArray[1][idx] = point[1]
        })
        return pointsArray
    }

    static def pointPairsToArrays(List pointPairs) {
        assert pointPairs.findAll({it.size() == 2}).size() == pointPairs.size()
        def srcPoints = pointPairs.collect({it.get(0)})
        def dstPoints = pointPairs.collect({it.get(1)})

        return [srcPoints, dstPoints].collect({pointsToArray(it)})
    }

    static def pointsFromHierarchy(PathObjectHierarchy hier) {
        return hier.getAnnotationObjects()
            .findAll({it.getROI() instanceof PointsROI && it.getROI().getNumPoints() == 1})
            .groupBy({it.getName()})
            .findAll({it.key != null && it.key.isInteger() && it.key.toInteger() > 0 && it.value.size() == 1})
            .collectEntries({[it.key.toInteger(), it.value.get(0)]})
            .collectEntries({[it.key, it.value.getROI().getAllPoints().get(0)]})
            .collectEntries({[it.key, [it.value.getX(), it.value.getY()]]})
    }

    static def pointArraysFromHierarchies(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        def (points1, points2) = [hier1, hier2].collect({pointsFromHierarchy(it)})
        def keys = points1.keySet().findAll({it in points2.keySet()})
        return pointPairsToArrays(keys.collect({[points1.get(it), points2.get(it)]}).toList())
    }

    static def pointsAsHierarchy(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        def (points1, points2) = [hier1, hier2].collect({def hier ->
            return hier.getAnnotationObjects()
                .findAll({it.getROI() instanceof PointsROI && it.getROI().getNumPoints() == 1})
                .groupBy({it.getName()})
                .findAll({it.key != null && it.key.isInteger() && it.key.toInteger() > 0 && it.value.size() == 1})
                .collectEntries({[it.key.toInteger(), it.value.get(0)]})
        })
        def hier = new PathObjectHierarchy()
        hier.addPathObjects(points1
            .findAll({it.key in points2.keySet()})
            .collect({
                def obj = PathObjects.createAnnotationObject(it.value.getROI())
                obj.setName(it.key.toString())
                return obj
            })
        )
        return hier
    }

    static def labelUnlabeledPoints(PathObjectHierarchy hier) {
        def grouped = hier.getAnnotationObjects()
            .findAll({it.getROI() instanceof PointsROI})
            .groupBy({it.getName()})
        if (null in grouped.keySet()) {
            def maxKey = grouped.keySet()
                .findAll({it != null && it.isInteger() && it.toInteger() > 0})
                .collect({it.toInteger()})
                .max()
            maxKey = 1 + ((maxKey == null) ? 0 : maxKey)
            grouped.get(null).withIndex().each({it.get(0).setName((it.get(1) + maxKey).toString())})
        }
    }

    static def getPairs(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        def (points1, points2) = [hier1, hier2].collect({PathObjectHierarchy hier ->
            hier.getAnnotationObjects()
                .findAll({it.getROI() instanceof PointsROI})
                .groupBy({it.getName()})
                .findAll({it.key != null && it.key.isInteger() && it.key.toInteger() > 0})
                .findAll({it.value.size() == 1})
                .collectEntries({[it.key.toInteger(), it.value.get(0)]})
        })

        return points1.keySet().intersect(points2.keySet()).collectEntries({[it, [points1.get(it), points2.get(it)]]})
    }

    // static def getNextPair(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
    //     def keys = getPairs(hier1, hier2).keySet()
    //     def ret = 1
    //     while (ret in keys)
    //         ret += 1
    //     return ret
    // }

    static def getNextPair(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        def (keys1, keys2) = [hier1, hier2].collect({PathObjectHierarchy hier ->
            hier.getAnnotationObjects()
                .findAll({it.getROI() instanceof PointsROI})
                .groupBy({it.getName()})
                .findAll({it.key != null && it.key.isInteger() && it.key.toInteger() > 0})
                .collect({it.key.toInteger()})
        })
        def ret = 1
        while (ret in keys1 || ret in keys2)
            ret += 1
        return ret
    }

    static def getEmptyPair(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        def points = [hier1, hier2].collect({PathObjectHierarchy hier ->
            def empty = hier.getAnnotationObjects()
                .findAll({it.getROI() instanceof PointsROI})
                .groupBy({it.getName()})
                .findAll({it.key == null})
                .collect({it.value})
            return (empty.size() > 0 ? empty.get(0) : empty)
                .findAll({hier.getSelectionModel().isSelected(it)})
        })

        if (points.collect({it.size()}).count(1) == 2)
            return points.collect({it.get(0)})
        return null
    }

    static def addEmptyPair(PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        def pair = getEmptyPair(hier1, hier2)
        if (pair == null)
            return false
        def max = getNextPair(hier1, hier2)
        pair.each({it.setName(Integer.toString(max))})
        return true
    }

    static def updateListView(ListView lv, PathObjectHierarchy hier1, PathObjectHierarchy hier2) {
        lv.setItems(FXCollections.observableArrayList(getPairs(hier1, hier2).keySet().sort().collect({it.toString()})))
    }
}


class Params {
    def paramDict
    def buttons

    def Params(Collection<ButtonType> buttons) {
        this.paramDict = [:]
        this.buttons = buttons
    }

    def add(String key, String label, Object param) {
        this.paramDict.put(key, [new Label(label), param])
    }

    def get(String key) {
        return this.paramDict.get(key)[1]
    }

    def buildGridPane() {
        def grid = new GridPane()
        grid.setHgap(10)
        grid.setVgap(10)
        this.paramDict.eachWithIndex({it, index -> grid.addRow(index, *it.value)})
        return grid
    }

    def buildPane() {
        def pane = new DialogPane()
        pane.setContent(this.buildGridPane())
        if (this.buttons != null)
            pane.getButtonTypes().setAll(this.buttons)
        return pane
    }

    static Callable<ButtonType> getAlertCallable(Stage owner, String title, String text) {
        Alert alert = new Alert(AlertType.INFORMATION)
        alert.initOwner(owner)
        alert.initModality(Modality.NONE)

        alert.setTitle(title)
        alert.setContentText(text)

        return new Callable<ButtonType>() {
            @Override ButtonType call() {
                def resp = alert.showAndWait()
                return resp.isPresent() ? null : resp.get()
            }
        }
    }

    static Callable<Integer> getDialogCallable(Stage owner, String title, DialogPane pane, boolean closable=true) {
        Dialog<ButtonType> dialog = new Dialog<>()
        dialog.initOwner(owner)
        dialog.initModality(Modality.NONE)

        dialog.setTitle(title)
        dialog.setDialogPane(pane)

        if (!closable)
            dialog.getDialogPane().getScene().getWindow().setOnCloseRequest(e -> {e.consume()})

        return new Callable<Integer>() {
            @Override Integer call() {
                Optional<ButtonType> resp = dialog.showAndWait()
                return resp.isPresent() ? dialog.getDialogPane().getButtonTypes().indexOf(resp.get()) : -1
            }
        }
    }
}


class ImageEntryBox extends ChoiceBox<ProjectImageEntry> {
    class ImageEntryConverter extends StringConverter<ProjectImageEntry> {
        Project project

        def ImageEntryConverter(Project project) {
            this.project = project
        }

        @Override String toString(ProjectImageEntry entry) {
            return entry == null ? "" : entry.getImageName()
        }

        @Override ProjectImageEntry fromString(String name) {
            def entries = this.project.getImageList().findAll({it.getImageName() == name})
            return entries.size() == 0 ? null : entries.get(0)
        }
    }

    def ImageEntryBox(Project project) {
        this(project, project.getImageList())
    }

    def ImageEntryBox(Project project, Collection<ProjectImageEntry> entries) {
        super()
        this.getItems().setAll(entries)
        this.setConverter(new ImageEntryConverter(project))
    }
}


class CustomPathPrefs {
    static String defaultRootNodeName = "/io.github.qupath/0.3"
    Preferences rootNode

    def CustomPathPrefs() {
        this(CustomPathPrefs.defaultRootNodeName)
    }

    def CustomPathPrefs(String rootNodeName) {
        this(rootNodeName, false)
    }

    def CustomPathPrefs(String rootNodeName, boolean create) {
        this.rootNode = CustomPathPrefs.getUserPrefsNode(rootNodeName, create)
        assert this.rootNode != null
    }

    static def getUserPrefsNode(String nodeName, boolean create) {
        def node = Preferences.userRoot()
        def nodeNames = nodeName.split("/")
        for (def name: nodeNames) {
            if (!create && !node.nodeExists(name))
                return null
            node = node.node(name)
        }
        return node
    }

    def getPrefs(String nodeName, String value) {
        return this.rootNode.get(nodeName, value)
    }

    def getPrefs(String nodeName, boolean value) {
        return this.rootNode.getBoolean(nodeName, value)
    }

    def setPrefs(String nodeName, String value) {
        this.rootNode.put(nodeName, value)
    }

    def setPrefs(String nodeName, boolean value) {
        this.rootNode.putBoolean(nodeName, value)
    }
}

class TPSClipboard {
    QuPathGUI gui
    TPSTransform transform
    final CustomPathPrefs prefs = new CustomPathPrefs(CustomPathPrefs.defaultRootNodeName + "/clipboard", true)
    Action action

    def TPSClipboard(QuPathGUI gui, TPSTransform transform) {
        this.gui = gui
        this.transform = transform

        if (this.prefs.getPrefs("", "") == "")
            this.prefs.setPrefs("", this.serialize([]).toString())

        GuiTools.runOnApplicationThread({
            def (section, label, shortcut) = ["Clipboard", "Paste transformed", "Ctrl+P"]
            def path = "${section}>${label}"
            def action = ActionTools.createAction(this.paste(), gui.parseName(path))
            action.setAccelerator(KeyCombination.keyCombination(shortcut))
            gui.addOrReplaceItem(gui.parseMenu(path, section, true).getItems(), ActionTools.createMenuItem(action))
        })
    }

    def transformPoint(double x, double y) {
        double[] dst = new double[2]
        transform.apply((double[])[x, y], dst)
        return dst.toList()
    }

    def transformPolygonROI(PolygonROI roi) {
        def points = roi.getAllPoints()
            .collect({(double[])[it.getX(), it.getY()]})
            .collect({
                double[] dst = new double[2]
                transform.apply(it, dst)
                return dst
            })
        def (pointsX, pointsY) = [0, 1].collect({def idx -> points.collect({it[idx]})})
        return ROIs.createPolygonROI((double[]) pointsX, (double[]) pointsY, roi.getImagePlane())
    }

    def jsonArrToList(JSONArray jsonArr) {
        return jsonArr.length() == 0 ? [] : (0 ..< jsonArr.length()).toArray().collect({jsonArr.get(it)})
    }

    String serializeROI(ROI roi) {
        def writer = new StringWriter()
        ROITypeAdapters.ROI_ADAPTER_INSTANCE.write(new JsonWriter(writer), roi)
        return writer.toString()
    }

    ROI deserializeROI(String json) {
        return ROITypeAdapters.ROI_ADAPTER_INSTANCE.read(new JsonReader(new StringReader(json)))
    }

    JSONArray serialize(List<PathAnnotationObject> anns) {
        return (new JSONArray(anns.collect({
            def map = [:]
            map.put("roi", this.serializeROI(it.getROI()))
            map.put("pathClasses", it.getPathClass() == null ? [] : PathClassTools.splitNames(it.getPathClass()))
            if (it.getName() != null)
                map.put("name", it.getName())
            if (it.getColor() != null)
                map.put("color", it.getColor())
            return map
        })))
    }

    List deserialize(String json) {
        return jsonArrToList(new JSONArray(json)).collect({
            def map = [:]
            map.put("roi", deserializeROI(it.get("roi")))
            map.put("pathClasses", (ArrayList<String>) jsonArrToList(it.get("pathClasses")))
            map.put("name", !it.has("name") ? null : (String) it.get("name"))
            map.put("color", !it.has("color") ? null : (Integer) it.get("color"))
            return map
        }).collect({
            def obj = PathObjects.createAnnotationObject(it.get("roi"), it.get("pathClasses").size() == 0 ? PathClass.getNullClass() : PathClass.fromArray(it.get("pathClasses").toArray()))
            obj.setName(it.get("name"))
            obj.setColor(it.get("color"))
            return obj
        })
    }

    Runnable paste() {
        return new Runnable() {
            @Override void run() {
                def anns = TPSClipboard.this.deserialize(TPSClipboard.this.prefs.getPrefs("", ""))
                def polygons = anns.findAll({it.getROI() instanceof PolygonROI})
                polygons.each({it.setROI(TPSClipboard.this.transformPolygonROI(it.getROI()))})
                TPSClipboard.this.gui.getViewer().getHierarchy().addPathObjects(polygons)
            }
        }
    }
}



class TPSTool implements Runnable {
    QuPathGUI gui

    def TPSTool(QuPathGUI gui) {
        this.gui = gui
    }

    static Params getParams(Project project, Collection<ProjectImageEntry> entries) {
        def params = new Params([ButtonType.CANCEL, ButtonType.APPLY, new ButtonType("Register points"), new ButtonType("Paste"), new ButtonType("Center")])

        params.add("image1", "Base image: ", new ImageEntryBox(project, entries))
        params.add("image2", "Transform image: ", new ImageEntryBox(project, entries))

        params.add("stiffness", "Stiffness: ", new Slider(0, 1, 0))

        params.add("opacity", "Overlay opacity: ", new Slider(0, 1, 1))

        params.add("overlay", "Overlay transform: ", new CheckBox())
        params.get("overlay").setSelected(true)

        params.add("hierarchy", "Overlay hierarchy: ", new CheckBox())
        params.get("hierarchy").setSelected(true)

        params.add("landmarks", "Landmarks: ", new ListView<String>())
        params.get("landmarks").setMaxHeight(24 * 5)
        params.get("landmarks").setEditable(true)
        // params.get("landmarks").setCellFactory(CheckBoxListCell.forListView(new Callback<Object, ObservableValue<Boolean>>() {
        //     @Override
        //     public ObservableValue<Boolean> call(Object obj) {
        //         return null;
        //     }
        // }))

        return params
    }

    static TPSClipboard getTPSClipboard(QuPathGUI gui, ImageData image1, ImageData image2, double stiffness=0.0) {
        return new TPSClipboard(gui, new TPSTransform(*PointUtils.pointArraysFromHierarchies(image1.getHierarchy(), image2.getHierarchy()), stiffness))
    }

    static TPSTransformServer getTransformServer(ImageData image1, ImageData image2, double stiffness=0.0) {
        def transform = new TPSTransform(*PointUtils.pointArraysFromHierarchies(image2.getHierarchy(), image1.getHierarchy()), stiffness)
        def transformServer = new TPSTransformServer(image2.getServer(), transform, ImageRegion.createInstance(0, 0, image1.getServer().getWidth(), image1.getServer().getHeight(), 0, 0))
        return transformServer
    }

    static ImageData putImageWithOverlay(QuPathGUI gui, QuPathViewer viewer, Project project, ImageServer baseImage, ImageServer overlayImage, PathObjectHierarchy hierarchy, DoubleProperty opacityProperty) {
        def image = new ImageData(*(hierarchy == null ? [baseImage] : [baseImage, hierarchy]))
        def overlay = new ServerOverlay(viewer, overlayImage)
        overlay.isOpacityProperty.set(true)
        overlay.opacityProperty.bind(opacityProperty)
        overlay.opacityProperty.addListener(new ChangeListener<? extends Number>() { @Override void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) { viewer.repaint() } })
        TPSTool.setImageWithOverlay(gui, viewer, project, image, overlay)
        return image
    }

    static void setImageWithOverlay(QuPathGUI gui, QuPathViewer viewer, Project project, ImageData image, PathOverlay overlay) {
        def prevImage = viewer.getImageData()
        def prevEntry = prevImage == null ? null : project.getEntry(prevImage)
        viewer.setImageData(image)
        viewer.getCustomOverlayLayers().setAll(overlay == null ? [] : overlay)
        if (prevEntry != null)
            project.removeImage(prevEntry, true)
        gui.projectBrowser.refreshProject()
    }

    static TPSTool getInstance(QuPathGUI gui) {
        return new TPSTool(gui)
    }

    void run() {
        def viewer = this.gui.getViewer()
        def owner = viewer.getView().getScene().getWindow()
        if (viewer.hasServer()) {
            Params.getAlertCallable(owner, "", "Main viewer is not empty").call()
            return
        }

        def clipboardTransform = null
        def project = this.gui.getProject()
        def images = this.gui.getViewers()
            .findAll({it != viewer})
            .findAll({it.hasServer()})
            .collect({it.getImageData()})
            .collectEntries({[project.getEntry(it), it]})
        def viewers = this.gui.getViewers()
            .findAll({it != viewer})
            .findAll({it.hasServer()})
            .collectEntries({[it.getImageData(), it]})

        def params = TPSTool.getParams(project, images.collect({it.key}))

        def pane = params.buildPane()
        pane.getButtonTypes().each({ButtonBar.setButtonUniformSize(pane.lookupButton(it), false)})
        def dialog = Params.getDialogCallable(owner, "TPS Transform", pane)
        def res = dialog.call()
        while (res != 0) {
            def (entry1, entry2) = ["image1", "image2"].collect({params.get(it).getValue()})
            if (!(null in [entry1, entry2]))
                PointUtils.updateListView(params.get("landmarks"), images.get(entry1).getHierarchy(), images.get(entry2).getHierarchy())

            if (res == 1) {
                if (entry1 == null || entry2 == null) {
                    Params.getAlertCallable(owner, "", "Fewer than two images selected").call()
                } else {
                    def (image1, image2) = [entry1, entry2].collect({images.get(it)})
                    double stiffness = params.get("stiffness").getValue()
                    boolean overlay = params.get("overlay").isSelected()
                    boolean hierarchy = params.get("hierarchy").isSelected()

                    clipboardTransform = TPSTool.getTPSClipboard(gui, image1, image2, stiffness)
                    def servers = [image1.getServer(), TPSTool.getTransformServer(image1, image2, stiffness)]
                    def imageData = TPSTool.putImageWithOverlay(gui, viewer, project, *(overlay ? servers : servers.reverse()), hierarchy ? PointUtils.pointsAsHierarchy(*([image1, image2].collect({it.getHierarchy()}))) : null, params.get("opacity").valueProperty())
                }
            } else if (res == 2) {
                if (!(null in [entry1, entry2])) {
                    PointUtils.addEmptyPair(*[entry1, entry2].collect({images.get(it).getHierarchy()}))
                    PointUtils.updateListView(params.get("landmarks"), images.get(entry1).getHierarchy(), images.get(entry2).getHierarchy())
                }
                    // .findAll({it != null})
                    // .collect({images.get(it)})
                    // .toSet()
                    // .each({PointUtils.labelUnlabeledPoints(it.getHierarchy())})
            } else if (res == 3) {
                // if (clipboardTransform != null) {
                //     def anns = clipboardTransform.deserialize(clipboardTransform.prefs.getPrefs("", ""))
                //     def polygons = anns.findAll({it.getROI() instanceof PolygonROI})
                //     polygons.each({it.setROI(clipboardTransform.transformPolygonROI(it.getROI()))})
                //     viewer.getHierarchy().addPathObjects(polygons)
                // }
                clipboardTransform.paste().run()
            } else if (res == 4) {
                def (image1, image2) = [entry1, entry2].collect({images.get(it)})
                def (viewer1, viewer2) = [entry1, entry2].collect({viewers.get(images.get(it))})

                // clipboardTransform = TPSTool.getTPSClipboard(gui, image1, image2, params.get("stiffness").getValue())
                def points = clipboardTransform.transformPoint(viewer2.getCenterPixelX(), viewer2.getCenterPixelY())
                viewer1.setCenterPixelLocation(*points)
                viewer.setCenterPixelLocation(*points)
            }

            res = dialog.call()
        }
        TPSTool.setImageWithOverlay(gui, viewer, project, null, null)
    }
}


def gui = QPEx.getQuPath().getInstance()
gui.installCommand("TPS Tool", new TPSTool(gui))

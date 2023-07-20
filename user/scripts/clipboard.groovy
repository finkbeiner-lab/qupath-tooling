import groovy.transform.InheritConstructors

import java.io.BufferedReader
import java.io.FileReader
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.prefs.Preferences

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.concurrent.Task
import javafx.event.EventHandler
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.DialogPane
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.Menu
import javafx.scene.control.MenuItem
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.input.KeyCombination
import javafx.scene.input.MouseEvent
import javafx.scene.layout.GridPane
import javafx.stage.Modality
import javafx.stage.FileChooser
import javafx.stage.DirectoryChooser
import javafx.util.StringConverter

import org.controlsfx.control.action.Action
import org.controlsfx.control.action.ActionUtils
import org.json.*

import qupath.lib.geom.Point2
import qupath.lib.gui.scripting.QPEx
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.ActionTools
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.tools.GuiTools
import qupath.lib.gui.tools.MenuTools
import qupath.lib.gui.viewer.QuPathViewer
import qupath.lib.io.GsonTools
import qupath.lib.io.ROITypeAdapters
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.PathDetectionObject
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.objects.PathObjectTools
import qupath.lib.objects.PathROIObject
import qupath.lib.objects.PathRootObject
import qupath.lib.objects.PathTileObject
import qupath.lib.objects.classes.PathClass
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.objects.classes.PathClassTools
import qupath.lib.objects.hierarchy.PathObjectHierarchy
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel
import qupath.lib.measurements.MeasurementList
import qupath.lib.measurements.MeasurementListFactory
import qupath.lib.regions.ImagePlane
import qupath.lib.regions.ImageRegion
import qupath.lib.roi.interfaces.ROI
import qupath.lib.roi.GeometryTools
import qupath.lib.roi.PolygonROI
import qupath.lib.roi.ROIs
import qupath.lib.roi.RoiTools


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


class Clipboard {
    QuPathGUI gui
    PathObjectHierarchy hier

    static final String prefsNodeName = "clipboard"
    final CustomPathPrefs prefs


    def Clipboard(QuPathGUI gui) {
        this.gui = gui
        this.prefs = new CustomPathPrefs(CustomPathPrefs.defaultRootNodeName + "/" + Clipboard.prefsNodeName, true)
        this.prefs.setPrefs("", this.serialize([]).toString())
    }

    def refresh() {
        def imageData = this.gui.getImageData()
        if (imageData == null)
            this.hier = null
        else
            this.hier = imageData.getHierarchy()
        return this.hier
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
            def obj = PathObjects.createAnnotationObject(it.get("roi"), it.get("pathClasses").size() == 0 ? PathClass.getNullClass() : PathClass.fromString(it.get("pathClasses")))
            obj.setName(it.get("name"))
            obj.setColor(it.get("color"))
            return obj
        })
    }


    def copy() {
        return new Runnable() {
            void run() {
                if (Clipboard.this.refresh() == null)
                    return
                def sel = Clipboard.this.hier.getSelectionModel()
                def anns = Clipboard.this.hier.getAnnotationObjects().findAll({sel.isSelected(it)})
                def serialized = Clipboard.this.serialize(anns).toString()
                Clipboard.this.prefs.setPrefs("", serialized)
            }
        }
    }

    def paste() {
        return new Runnable() {
            void run() {
                if (Clipboard.this.refresh() == null)
                    return
                def deserialized = Clipboard.this.deserialize(Clipboard.this.prefs.getPrefs("", ""))
                Clipboard.this.hier.addPathObjects(deserialized)
            }
        }
    }

    static Clipboard getInstance(QuPathGUI gui) {
        return new Clipboard(gui)
    }
}


return Clipboard

import java.util.concurrent.Callable;
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.event.EventType;
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
import javafx.scene.control.Menu;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCharacterCombination;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
// import javafx.util.Callback;

import qupath.lib.gui.scripting.QPEx
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.viewer.QuPathViewer

import qupath.lib.objects.PathObject
import qupath.lib.objects.PathAnnotationObject
import qupath.lib.objects.classes.PathClass
import qupath.lib.objects.classes.PathClassFactory
import qupath.lib.objects.classes.PathClassTools
import qupath.lib.objects.hierarchy.PathObjectHierarchy
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel


abstract class Callback<T> {
    abstract void call(T obj)
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

    static Callable<Integer> getDialogCallable(Stage owner, String title, DialogPane pane) {
        Dialog<ButtonType> dialog = new Dialog<>()
        dialog.initOwner(owner)
        dialog.initModality(Modality.NONE)

        dialog.setTitle(title)
        dialog.setDialogPane(pane)

        return new Callable<Integer>() {
            @Override Integer call() {
                Optional<ButtonType> resp = dialog.showAndWait()
                return resp.isPresent() ? dialog.getDialogPane().getButtonTypes().indexOf(resp.get()) : -1
            }
        }
    }

    static ButtonType getAlert(String text) {
        (new Params()).getAlertCallable(QPEx.getQuPath().getInstance().getStage(), "", text).call()
    }
}


class CustomPathPrefs {
    static String defaultRootNodeName = "/io.github.qupath/0.3"
    Preferences rootNode

    def CustomPathPrefs() {
        this("")
    }

    def CustomPathPrefs(String nodeName) {
        this(nodeName, false)
    }

    def CustomPathPrefs(String nodeName, boolean create) {
        this.rootNode = CustomPathPrefs.getUserPrefsNode(CustomPathPrefs.defaultRootNodeName + "/" + nodeName, create)
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


class KeyPairFilter implements EventHandler<KeyEvent> {
    final CustomPathPrefs prefs = new CustomPathPrefs("hotkeys", true)

    String name
    Scene scene

    Map<KeyCode, Callback<KeyCode>> actions

    KeyCode action
    boolean handling = false
    boolean handled = false

    def KeyPairFilter(String name, Scene scene, Map<KeyCode, Callback<KeyCode>> actions) {
        this.name = name
        this.prefs.setPrefs(this.name, this.hashCode().toString())
        this.scene = scene
        this.actions = actions

        this.scene.addEventFilter(KeyEvent.ANY, this)
    }

    @Override void handle(KeyEvent e) {
        if (this.prefs.getPrefs(this.name, "") != this.hashCode().toString())
            return

        if (!this.handled) {
            if (!this.handling && e.getEventType() == KeyEvent.KEY_PRESSED && e.isControlDown() && e.getCode() in this.actions.keySet()) {
                e.consume()
                this.action = e.getCode()
                this.handling = true
            } else if (this.handling) {
                if (e.getEventType() == KeyEvent.KEY_PRESSED)
                    this.handling = false
                else {
                    e.consume()
                    if (e.getEventType() == KeyEvent.KEY_RELEASED && e.getCode() == KeyCode.CONTROL)
                        this.handled = true
                }
            }
        } else {
            e.consume()
            if (e.getEventType() == KeyEvent.KEY_RELEASED) {
                this.handling = false
                this.handled = false
                this.actions.get(this.action).call(e.getCode())
            }
        }

    }
}



class PathClassSetter {
    static boolean setClass(PathObject po, PathClass pc) {
        PathClass pcOld = po.getPathClass();
        po.setPathClass(pc);
        return po.getPathClass() != pcOld;
    }

    static List<PathObject> setClassChanged(List<PathObject> pos, PathClass pc) {
        List<PathObject> changed = new ArrayList<>();
        for (PathObject po: pos) {
            if (setClass(po, pc))
                changed.add(po);
        }
        return changed;
    }

    static void setSelectedAnnotationsClass(QuPathGUI instance, QuPathViewer viewer, PathClass pc) {
        if (viewer != null) {
            PathObjectHierarchy hier = viewer.getHierarchy();
            PathObjectSelectionModel sel = hier.getSelectionModel();
            List<PathObject> change = new ArrayList<>();
            for (PathObject po: hier.getAnnotationObjects()) {
                if (sel.isSelected(po))
                    change.add(po);
            }
            hier.fireObjectClassificationsChangedEvent(instance, setClassChanged(change, pc));
        }
    }
}


class PathClassIntensitySetter {
    static PathClass createIntensityClass(PathClass pc, int intensity) {
        pc = PathClassTools.getNonIntensityAncestorClass(pc);
        if (intensity == 0)
            return PathClassFactory.getNegative(pc);
        if (intensity == 1)
            return PathClassFactory.getOnePlus(pc);
        if (intensity == 2)
            return PathClassFactory.getTwoPlus(pc);
        if (intensity == 3)
            return PathClassFactory.getThreePlus(pc);
        if (intensity == 4)
            return PathClassFactory.getPositive(pc);
        return pc;
    }

    static boolean setIntensityClass(PathObject po, int intensity) {
        PathClass pc = po.getPathClass();
        po.setPathClass(createIntensityClass(pc, intensity));
        return po.getPathClass() != pc;
    }

    static List<PathObject> setIntensityClassChanged(List<PathObject> pos, int intensity) {
        List<PathObject> changed = new ArrayList<>();
        for (PathObject po: pos) {
            if (setIntensityClass(po, intensity))
                changed.add(po);
        }
        return changed;
    }

    static void setSelectedAnnotationsIntensityClass(QuPathGUI instance, QuPathViewer viewer, int intensity) {
        if (viewer != null) {
            PathObjectHierarchy hier = viewer.getHierarchy();
            PathObjectSelectionModel sel = hier.getSelectionModel();
            List<PathObject> change = new ArrayList<>();
            for (PathObject po: hier.getAnnotationObjects()) {
                if (sel.isSelected(po))
                    change.add(po);
            }
            hier.fireObjectClassificationsChangedEvent(instance, setIntensityClassChanged(change, intensity));
        }
    }
}


class PathClassHotkeys {
    QuPathGUI gui
    KeyPairFilter filter

    def PathClassHotkeys(QuPathGUI gui) {
        this.gui = gui
    }

    def PathClassHotkeys(QuPathGUI gui, Map<String, List> pathClassGroups) {
        this(gui)
        this.filter = new KeyPairFilter("hotkeys", this.gui.getStage().getScene(), pathClassGroups.collectEntries({ String key, List value -> [
            KeyCode."${value.get(0)}",
            key.size() == 0 ? this.getIntensityCallback() : this.getPathClassCallback(this.getPathClassMap(key, value.get(1).collect({it.key})))
        ]}))
    }

    KeyPairFilter getFilter(Map<KeyCode, Callback<KeyCode>> map) {
        return new KeyPairFilter("hotkeys", this.gui.getStage().getScene(), map)
    }

    Map<KeyCode, PathClass> getPathClassMap(String baseName, List<String> names) {
        assert names.size() <= 9
        return ([PathClass.fromString(baseName)] + names.collect({PathClass.fromArray(baseName, it)})).withIndex().collectEntries({[it.get(1) == 0 ? KeyCode.SPACE : KeyCode."DIGIT${it.get(1)}", it.get(0)]})
    }

    Callback<KeyCode> getPathClassCallback(Map<KeyCode, PathClass> pcs) {
        return new Callback<KeyCode>() {
            final Map<KeyCode, PathClass> pathClassMap = pcs
            @Override void call(KeyCode val) {
                if (val in pathClassMap.keySet())
                    PathClassSetter.setSelectedAnnotationsClass(PathClassHotkeys.this.gui, PathClassHotkeys.this.gui.getViewer(), this.pathClassMap.get(val))
            }
        }
    }

    Callback<KeyCode> getIntensityCallback() {
        return new Callback<KeyCode>() {
            final Map<KeyCode, Integer> intensityMap = (-1 .. 4).toList().collectEntries({[it == -1 ? KeyCode.SPACE : KeyCode."DIGIT${it}", it]})
            @Override void call(KeyCode val) {
                Integer code = this.intensityMap.get(val, null)
                if (code != null)
                    PathClassIntensitySetter.setSelectedAnnotationsIntensityClass(PathClassHotkeys.this.gui, PathClassHotkeys.this.gui.getViewer(), code)
            }
        }
    }

    static PathClassHotkeys getInstance(QuPathGUI gui, Map<String, List> pathClassGroups) {
        return new PathClassHotkeys(gui, pathClassGroups)
    }
}


return PathClassHotkeys

import java.io.File

import javafx.scene.input.KeyCombination

import qupath.lib.common.ColorTools
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.ActionTools
import qupath.lib.gui.scripting.QPEx
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.gui.tools.GuiTools
import qupath.lib.gui.viewer.OverlayOptions
import qupath.lib.objects.classes.PathClassFactory


// Example:
//     [
//         "": [hotkey_confidence, []]
//
//         group_1: [hotkey_1, [
//             name_1_1: [red_1_1, blue_1_1, green_1_1],
//             name_1_2: [red_1_2, blue_1_2, green_1_2],
//             ...
//         ]],
//
//         group_2: [hotkey_2, [
//             name_2_1: [red_2_1, blue_2_1, green_2_1],
//             name_2_2: [red_2_2, blue_2_2, green_2_2],
//             ...
//         ]],
//
//         ...
//     ]
def pathClassGroups = [
    "": ["Q", []],

    "Amyloid plaque": ["A", [
        "Diffuse": [0, 255, 0],
        "Coarse": [0, 255, 255],
        "Core": [255, 153, 102],
        "Burnt-out": [150, 79, 239],
    ]],

    "Amyloid angiopathy": ["D", [
        "Grade 1": [0, 255, 0],
        "Grade 2": [0, 255, 255],
        "Grade 3": [255, 153, 102],
        "Grade 4": [150, 79, 239],
    ]],

    "Neuronal tau": ["T", [
        "Pre": [0, 255, 0],
        "Mature": [0, 255, 255],
        "Ghost": [255, 153, 102],
    ]],

    "Glial tau": ["E", [
        "Astrocyte": [0, 255, 0],
        "Oligodendrocyte": [0, 255, 255],
    ]],

    "Other tau": ["R", [
        "TANC": [0, 255, 0],
        "Dystrophic neurite": [0, 255, 255],
        "Neuropil thread": [255, 153, 102],
        "Granule": [150, 79, 239],
    ]]
]

def prefMap = [
    "gridScaleMicrons": (boolean) false,
    "gridSpacingX": (double) 1024,
    "gridSpacingY": (double) 1024,
    "gridStartX": (double) 0,
    "gridStartY": (double) 0,

    "multipointTool": (boolean) false,
]

def overlayOptionsMap = [
    "fillDetections": (boolean) true,
    "opacity": (float) 1,
    "showAnnotations": (boolean) true,
    "showDetections": (boolean) true,
    "showGrid": (boolean) true,
    "showPixelClassification": (boolean) true,
    "showTMAGrid": (boolean) true,
]


class ScriptLoader {
   static File scriptsPath = new File(new File(PathPrefs.userPathProperty().get()), "scripts")
    // static File scriptsPath = new File(new File(PathPrefs.userPathProperty().get()), "scripts/startup")
    
    def ScriptLoader() {
        assert this.scriptsPath.isDirectory()
    }

    def getScriptFile(String path) {
        return new File(this.scriptsPath, path)
    }

    def getScript(String path) {
        def scriptFile = this.getScriptFile(path)
        assert scriptFile.isFile()
        return Eval.me(scriptFile.getText())
    }

    def getScriptOptional(String path) {
        def scriptFile = this.getScriptFile(path)
        return scriptFile.isFile() ? Eval.me(scriptFile.getText()) : null
    }
}


def setPrefs(Map<String, Object> map) {
    return map
        .collectEntries({[it.key, PathPrefs.@"${it.key}"]})
        .findAll({it.value.get() != map.get(it.key)})
        .each({it.value.set(map.get(it.key))})
}

def setOverlayOptions(OverlayOptions overlayOptions, Map<String, Object> map) {
    return GuiTools.callOnApplicationThread({
        return map
            .collectEntries({[it.key, overlayOptions.@"${it.key}"]})
            .findAll({it.value.get() != map.get(it.key)})
            .each({it.value.set(map.get(it.key))})
    })
}

def setPathClasses(QuPathGUI gui, Map<String, List> map) {
    GuiTools.runOnApplicationThread({
        gui.getAvailablePathClasses().setAll([
            PathClass.getNullClass(),
            *map.findAll({it.key.size() > 0}).collectEntries({String group, List vals ->
                [group, vals.get(1).collect({
                    assert it.value.size() == 3
                    def pathClass = PathClass.fromArray(group, it.key)
                    pathClass.setColor(ColorTools.packRGB(*it.value))
                    return pathClass
                })]
            }).values().sum()
        ])
    })
}


def loadClipboard(QuPathGUI gui) {
    def clipboard = (new ScriptLoader()).getScript("clipboard.groovy").getInstance(gui)
    def section = "Clipboard"
    [["copy", "Copy", "Ctrl+C"], ["paste", "Paste", "Ctrl+V"]].each({ String func, String label, String shortcut ->
        GuiTools.runOnApplicationThread({
            def path = "${section}>${label}"
            def action = ActionTools.createAction(clipboard."${func}"(), gui.parseName(path))
            action.setAccelerator(KeyCombination.keyCombination(shortcut))
            gui.addOrReplaceItem(gui.parseMenu(path, section, true).getItems(), ActionTools.createMenuItem(action))
        })
    })
    return true
}

def loadHotkeys(QuPathGUI gui, Map<String, Map<String, String>> pathClassHotkeys) {
    def hotkeys = (new ScriptLoader()).getScript("hotkeys.groovy").getInstance(gui, pathClassHotkeys)
    return true
}

def loadContextMenu(QuPathGUI gui) {
    def contextMenu = (new ScriptLoader()).getScript("contextMenu.groovy").getInstance(gui)
    gui.installCommand("Custom context menu", contextMenu)
    contextMenu.run()
    return true
}

def loadTileManager(QuPathGUI gui) {
    def tileManager = (new ScriptLoader()).getScript("tileManager.groovy").getInstance(gui)
    gui.installCommand("Tile Manager", tileManager)
    return true
}


def gui = QPEx.getQuPath().getInstance()

setPrefs(prefMap)
setOverlayOptions(gui.getOverlayOptions(), overlayOptionsMap)
setPathClasses(gui, pathClassGroups)

loadClipboard(gui)
loadHotkeys(gui, pathClassGroups)
loadContextMenu(gui)
loadTileManager(gui)

/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.extension.svg;

import org.controlsfx.control.action.Action;

import qupath.lib.common.Version;
import qupath.lib.extension.svg.SvgExportCommand.SvgExportType;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathResources;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Extension for SVG image export.
 */
public class SvgExtension implements QuPathExtension {
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	var svgActions = new SvgActions(qupath);
    	var actions = ActionTools.getAnnotatedActions(svgActions);
    	qupath.installActions(actions);
    }

    @Override
    public String getName() {
    	return QuPathResources.getString("Extension.SVG");
    }

    @Override
    public String getDescription() {
        return QuPathResources.getString("Extension.SVG.description");
    }
	
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return getVersion();
	}
	
	
	public class SvgActions {
		
		@ActionMenu(value = {"Menu.File", "Menu.File.ExportImage"})
		@ActionConfig("Action.SVG.exportImage")
		public final Action actionExport;
		
		@ActionMenu(value = {"Menu.File", "Menu.File.ExportSnapshot"})
		@ActionConfig("Action.SVG.exportSnapshot")
		public final Action actionSnapshot;
		
		SvgActions(QuPathGUI qupath) {
			
			actionExport = ActionTools.createAction(new SvgExportCommand(qupath, SvgExportType.SELECTED_REGION));
			actionSnapshot = ActionTools.createAction(new SvgExportCommand(qupath, SvgExportType.VIEWER_SNAPSHOT));
			
		}
		
	}
	
	
}
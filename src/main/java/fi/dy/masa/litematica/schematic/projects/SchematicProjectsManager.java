package fi.dy.masa.litematica.schematic.projects;

import java.io.File;
import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.util.EntityUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

public class SchematicProjectsManager
{
    //private static final Pattern PATTERN_NAME_NUMBER = Pattern.compile("(.*)([0-9]+)$");
    @Nullable private SchematicProject currentProject;

    @Nullable
    public SchematicProject getCurrentProject()
    {
        return this.currentProject;
    }

    public boolean hasProjectOpen()
    {
        return this.currentProject != null;
    }

    public void createAndOpenProject(File dir, String projectName)
    {
        this.closeCurrentProject();

        this.currentProject = new SchematicProject(dir, new File(dir, projectName + ".json"));
        this.currentProject.setName(projectName);
        this.currentProject.setOrigin(EntityUtils.getCameraEntityBlockPos());
        this.currentProject.saveToFile();
    }

    @Nullable
    public SchematicProject openProject(File projectFile)
    {
        this.closeCurrentProject();
        this.currentProject = this.loadProjectFromFile(projectFile, true);
        return this.currentProject;
    }

    @Nullable
    public SchematicProject loadProjectFromFile(File projectFile, boolean createPlacement)
    {
        if (projectFile.getName().endsWith(".json") && projectFile.exists() && projectFile.isFile() && projectFile.canRead())
        {
            JsonElement el = JsonUtils.parseJsonFile(projectFile);

            if (el != null && el.isJsonObject())
            {
                return SchematicProject.fromJson(el.getAsJsonObject(), projectFile, createPlacement);
            }
        }

        return null;
    }

    public void closeCurrentProject()
    {
        if (this.currentProject != null)
        {
            this.currentProject.saveToFile();
            this.removeCurrentPlacement();
            this.clear();
        }
    }

    public void saveCurrentProject()
    {
        if (this.currentProject != null)
        {
            this.currentProject.saveToFile();
        }
    }

    private void removeCurrentPlacement()
    {
        if (this.currentProject != null)
        {
            this.currentProject.removeCurrentPlacement();
        }
    }

    public void clear()
    {
        this.currentProject = null;
    }

    public boolean cycleVersion(int amount)
    {
        if (this.currentProject != null)
        {
            return this.currentProject.cycleVersion(amount);
        }
        else
        {
            MessageDispatcher.error().translate("litematica.error.schematic_projects.no_project_open");
        }

        return false;
    }

    public boolean commitNewVersion(String string)
    {
        if (this.currentProject != null)
        {
            return this.currentProject.commitNewVersion(string);
        }
        else
        {
            MessageDispatcher.error().translate("litematica.error.schematic_projects.no_project_open");
        }

        return false;
    }

    public boolean pasteCurrentVersionToWorld()
    {
        SchematicProject project = this.getCurrentProject();

        if (project != null)
        {
            project.pasteToWorld();
            return true;
        }

        return false;
    }

    public boolean deleteLastSeenArea()
    {
        SchematicProject project = this.getCurrentProject();

        if (project != null)
        {
            project.deleteLastSeenArea();
            return true;
        }

        return false;
    }

    public JsonObject toJson()
    {
        JsonObject obj = new JsonObject();

        if (this.currentProject != null)
        {
            obj.add("current_project", new JsonPrimitive(this.currentProject.getProjectFile().getAbsolutePath()));
        }

        return obj;
    }

    public void loadFromJson(JsonObject obj)
    {
        if (JsonUtils.hasString(obj, "current_project"))
        {
            File file = new File(JsonUtils.getString(obj, "current_project"));
            this.currentProject = this.loadProjectFromFile(file, true);
        }
    }
}

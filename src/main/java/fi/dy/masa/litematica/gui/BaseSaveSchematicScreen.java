package fi.dy.masa.litematica.gui;

import java.io.File;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;
import fi.dy.masa.malilib.gui.icon.DefaultIcons;
import fi.dy.masa.malilib.gui.widget.BaseTextFieldWidget;
import fi.dy.masa.malilib.gui.widget.DropDownListWidget;
import fi.dy.masa.malilib.gui.widget.IconWidget;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import fi.dy.masa.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import fi.dy.masa.malilib.overlay.message.MessageDispatcher;
import fi.dy.masa.malilib.util.FileNameUtils;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.SchematicType;

public abstract class BaseSaveSchematicScreen extends BaseSchematicBrowserScreen
{
    protected final BaseTextFieldWidget fileNameTextField;
    protected final GenericButton revertNameButton;
    protected final GenericButton saveButton;
    protected final DropDownListWidget<SchematicType<?>> schematicTypeDropdown;
    protected String originalName = "";

    protected BaseSaveSchematicScreen(int listX, int listY,
                                      int totalListMarginX, int totalListMarginY,
                                      String browserContext)
    {
        super(listX, listY, totalListMarginX, totalListMarginY, browserContext);

        this.fileNameTextField = new BaseTextFieldWidget(320, 16);
        this.fileNameTextField.setFocused(true);

        this.revertNameButton = GenericButton.create(DefaultIcons.RESET_12, this::revertName);
        this.saveButton = GenericButton.create(18, "litematica.button.save_schematic.save_schematic", this::saveSchematic);

        this.schematicTypeDropdown = new DropDownListWidget<>(18, 6, SchematicType.KNOWN_TYPES, SchematicType::getDisplayName, (e) -> new IconWidget(e.getIcon()));
        this.schematicTypeDropdown.setSelectedEntry(SchematicType.LITEMATICA);

        this.revertNameButton.translateAndAddHoverString("litematica.hover.button.save_schematic.revert_name");
        this.saveButton.translateAndAddHoverString("litematica.hover.button.save_schematic.hold_shift_to_overwrite");
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.fileNameTextField);
        this.addWidget(this.revertNameButton);
        this.addWidget(this.saveButton);
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        this.fileNameTextField.setPosition(this.x + 10, this.y + 24);
        this.revertNameButton.setX(this.fileNameTextField.getRight() + 2);
        this.revertNameButton.centerVerticallyInside(this.fileNameTextField);
    }

    @Override
    public void onSelectionChange(@Nullable DirectoryEntry entry)
    {
        super.onSelectionChange(entry);

        if (entry != null && entry.getType() == DirectoryEntryType.FILE)
        {
            String name = FileNameUtils.getFileNameWithoutExtension(entry.getName());
            this.fileNameTextField.setText(name);
        }
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers)
    {
        if (this.fileNameTextField.isFocused() && keyCode == Keyboard.KEY_RETURN)
        {
            this.saveSchematic();
            return true;
        }
        else if (this.fileNameTextField.onKeyTyped(keyCode, scanCode, modifiers))
        {
            this.getListWidget().clearSelection();
            return true;
        }

        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }

    protected void revertName()
    {
        this.fileNameTextField.setText(this.originalName);
        this.fileNameTextField.setFocused(true);
    }

    protected abstract void saveSchematic();

    @Nullable
    protected File getSchematicFileIfCanSave(boolean isHoldingShift)
    {
        String name = this.fileNameTextField.getText();
        File dir = this.getListWidget().getCurrentDirectory();

        if (dir.isDirectory() == false)
        {
            MessageDispatcher.error("litematica.message.error.save_schematic.invalid_directory", dir.getAbsolutePath());
            return null;
        }

        if (StringUtils.isBlank(name))
        {
            MessageDispatcher.error("litematica.message.error.save_schematic.no_file_name");
            return null;
        }

        if (FileNameUtils.doesFileNameContainIllegalCharacters(name))
        {
            MessageDispatcher.error("malilib.message.error.illegal_characters_in_file_name", name);
            return null;
        }

        SchematicType<?> outputType = this.schematicTypeDropdown.getSelectedEntry();

        if (outputType == null)
        {
            MessageDispatcher.error("litematica.message.error.save_schematic.no_type_selected");
            return null;
        }

        File file = new File(dir, name + outputType.getFileNameExtension());

        if (isHoldingShift == false && file.exists())
        {
            MessageDispatcher.error("litematica.message.error.file_exists.hold_shift_to_override", file.getName());
            return null;
        }

        return file;
    }

    public static String getDefaultFileNameForSchematic(ISchematic schematic)
    {
        File file = schematic.getFile();

        if (file != null)
        {
            return FileNameUtils.getFileNameWithoutExtension(file.getName());
        }
        else
        {
            return getFileNameFromDisplayName(schematic.getMetadata().getName());
        }
    }

    public static String getFileNameFromDisplayName(String name)
    {
        if (Configs.Generic.GENERATE_LOWERCASE_NAMES.getBooleanValue())
        {
            name = FileNameUtils.generateSimpleSafeFileName(name);
        }

        return name;
    }
}

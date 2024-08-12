package org.cirdles.tripoli.gui.settings.color.fxcomponents;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import java.io.IOException;

public class SpeciesColorSelectionRow extends HBox {

    @FXML
    private ColorPickerSplotch colorPickerSplotch;
    @FXML
    private Label rowLabel;
    private final ObjectProperty<Color> colorProperty;
    private final ObjectProperty<String> textProperty;

    public SpeciesColorSelectionRow() {
        super();
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("SpeciesColorSelectionRow.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        try{
            fxmlLoader.load();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        this.colorProperty = new SimpleObjectProperty<>();
        this.textProperty = new SimpleObjectProperty<>();
        textProperty.bindBidirectional(rowLabel.textProperty());
        colorProperty.bindBidirectional(colorPickerSplotch.colorProperty());
    }


    public ColorPickerSplotch getColorPickerSplotch() {
        return colorPickerSplotch;
    }

    public Label getRowLabel() {
        return rowLabel;
    }

    public Color getColorProperty() {
        return colorProperty.get();
    }

    public ObjectProperty<Color> colorObjectProperty() {
        return colorProperty;
    }

    public void setColorProperty(Color colorProperty) {
        this.colorProperty.set(colorProperty);
    }

    public String getTextProperty() {
        return textProperty.get();
    }

    public ObjectProperty<String> textObjectProperty() {
        return textProperty;
    }

    public void setTextProperty(String textProperty) {
        this.textProperty.set(textProperty);
    }
}

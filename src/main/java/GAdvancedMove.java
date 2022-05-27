import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.HEntity;
import gearth.extensions.parsers.HEntityUpdate;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;
import java.io.*;
import java.util.logging.LogManager;

@ExtensionInfo(
        Title = "GAdvancedMove",
        Description = "My first version",
        Version = "1.0.0",
        Author = "Julianty"
)

public class GAdvancedMove extends ExtensionForm implements NativeKeyListener {
    public String yourName;
    public int yourIndex = -1;
    public int userX, userY;

    public TextField txtHotKeyLeft, txtHotKeyRight, txtHotKeyUp, txtHotKeyDown,
            txtHotKeyLowerRight, txtHotKeyLowerLeft, txtHotKeyUpperRight, txtHotKeyUpperLeft;
    public TextField txtSteps;
    public RadioButton radioButtonWalk, radioButtonRun;
    public Text txtInformation;

    // GAdvancedMove.class.getProtectionDomain().getCodeSource().getLocation().getPath();   // Obtiene la ubicacion de la clase
    TextInputControl[] txtFieldsHotKeys;
    public Boolean isFocused;

    @Override
    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {}

    @Override
    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
        String keyTxt = NativeKeyEvent.getKeyText(nativeKeyEvent.getKeyCode());

        for (TextInputControl txtControl : txtFieldsHotKeys) {
            if(txtControl.isFocused()){    // Algun control tiene el foco
                txtControl.setText(keyTxt);
                Platform.runLater(txtInformation::requestFocus); // Le da el foco al control
            }
            else if(!txtControl.isFocused()){
                if(txtControl.getText().equals(keyTxt)){
                    int steps = Integer.parseInt(txtSteps.getText());
                    if(isFocused){  // isFocused: solo si la ultima vez la ventana tuvo el foco se movera
                        if(keyTxt.equals(txtHotKeyUp.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX, userY - steps));
                        }
                        else if(keyTxt.equals(txtHotKeyDown.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX, userY + steps));
                        }
                        else if(keyTxt.equals(txtHotKeyLeft.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX - steps, userY));
                        }
                        else if(keyTxt.equals(txtHotKeyRight.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX + steps, userY));
                        }
                        else if(keyTxt.equals(txtHotKeyUpperRight.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX + steps, userY - steps));
                        }
                        else if(keyTxt.equals(txtHotKeyUpperLeft.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX - steps, userY - steps));
                        }
                        else if(keyTxt.equals(txtHotKeyLowerRight.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX + steps, userY + steps));
                        }
                        else if(keyTxt.equals(txtHotKeyLowerLeft.getText())){
                            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, userX - steps, userY + steps));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {}

    @Override
    protected void onShow() {
        // When its sent, get UserObject packet
        sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
        // When its sent, get UserIndex without restart room
        sendToServer(new HPacket("AvatarExpression", HMessage.Direction.TOSERVER, 0));
        // When its sent, get wallitems, flooritems and other things without restart room
        sendToServer(new HPacket("GetHeightMap", HMessage.Direction.TOSERVER));

        LogManager.getLogManager().reset();
        try {
            GlobalScreen.registerNativeHook();
        }
        catch (NativeHookException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
        GlobalScreen.addNativeKeyListener(this);
    }

    @Override
    protected void onHide() {
        yourIndex = -1;
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException nativeHookException) {
            nativeHookException.printStackTrace();
        }
    }

    @Override
    protected void initExtension() {
        // Init when you install the extension
        txtFieldsHotKeys = new TextInputControl[]{txtHotKeyUpperRight, txtHotKeyUpperLeft, txtHotKeyUp,
                txtHotKeyDown, txtHotKeyLeft, txtHotKeyRight, txtHotKeyLowerLeft, txtHotKeyLowerRight};

        // Detecta si la ventana tiene el foco o no
        primaryStage.focusedProperty().addListener((observable, oldValue, newValue) -> {
            isFocused = newValue;
            if(isFocused){
                Platform.runLater(() -> txtInformation.requestFocus()); // Le da el foco al control
            }
        });

        // Detecta si la ventana esta minimizada o no
        // primaryStage.iconifiedProperty().addListener((observable, oldValue, newValue) -> {});

        txtSteps.textProperty().addListener((observable, oldValue, newValue) -> {
            try {
                Integer.parseInt(txtSteps.getText());
            } catch (NumberFormatException e) {
                txtSteps.setText(oldValue);
            }
        });

        // Response of packet InfoRetrieve
        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            // Gets Name and ID in order.
            int YourID = hMessage.getPacket().readInteger();
            yourName = hMessage.getPacket().readString();
        });

        // Response of packet AvatarExpression
        intercept(HMessage.Direction.TOCLIENT, "Expression", hMessage -> {
            // First integer is index in room, second is animation id, i think
            if(primaryStage.isShowing() && yourIndex == -1){ // this could avoid any bug
                yourIndex = hMessage.getPacket().readInteger();
                txtInformation.setText(String.format("Index: %d ; Coords: (%d, %d)", yourIndex, userX, userY));
            }
        });

        // Intercept this packet when you enter or restart a room
        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HEntity[] roomUsersList = HEntity.parse(hMessage.getPacket());
                for (HEntity hEntity: roomUsersList){
                    if(hEntity.getName().equals(yourName)){    // In another room, the index changes
                        yourIndex = hEntity.getIndex();      // The userindex has been restarted
                        txtInformation.setText(String.format("Index: %d ; Coords: (%d, %d)", yourIndex, userX, userY));
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        });

        intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            try {
                for (HEntityUpdate hEntityUpdate: HEntityUpdate.parse(hPacket)){
                    int CurrentIndex = hEntityUpdate.getIndex();  // HEntityUpdate class allows get UserIndex
                    if(yourIndex == CurrentIndex){
                        // fix bug roller (important), also update the coords when you entry to the room
                        int jokerX = hEntityUpdate.getTile().getX();    int jokerY = hEntityUpdate.getTile().getY();

                        if(radioButtonWalk.isSelected()){ // getTile() es called when you entry a room
                            userX = hEntityUpdate.getTile().getX();  userY = hEntityUpdate.getTile().getY();
                        }
                        else if(radioButtonRun.isSelected()){
                            userX = jokerX;  userY = jokerY;      // fix bug roller, because the coordinate is not updated
                            txtInformation.setText(String.format("Index: %d ; Coords: (%d, %d)", yourIndex, userX, userY)); // Update the GUI

                            userX = hEntityUpdate.getMovingTo().getX();  userY = hEntityUpdate.getMovingTo().getY();
                        }
                        txtInformation.setText(String.format("Index: %d ; Coords: (%d, %d)", yourIndex, userX, userY));
                    }
                }
            } catch (NullPointerException ignored){}
        });
    }

    public String notepadStructure(){
        return String.format("HotKeyUp:%s\n" + "HotKeyDown:%s\n" + "HotKeyLeft:%s\n" + "HotKeyRight:%s\n" +
                "HotKeyUpperLeft:%s\n" + "HotKeyUpperRight:%s\n" + "HotKeyLowerLeft:%s\n" + "HotKeyLowerRight:%s"
                , txtHotKeyUp.getText(), txtHotKeyDown.getText(), txtHotKeyLeft.getText(), txtHotKeyRight.getText(),
                txtHotKeyUpperLeft.getText(), txtHotKeyUpperRight.getText(), txtHotKeyLowerLeft.getText(), txtHotKeyLowerRight.getText());
    }

    public void saveConfig() throws RuntimeException, IOException {
        // Configuracion de la ventana para guardar
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Configuration");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text File", "*.txt"));
        File file = fileChooser.showSaveDialog(primaryStage);

        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(notepadStructure());
            fileWriter.flush();
            fileWriter.close();
        }catch (RuntimeException ignored){} // Evita una excepcion cuando se cierra la ventana sin guardar
    }

    public void loadConfig() throws IOException, RuntimeException{
        // Configuracion de la ventana para cargar
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Configuration");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text File", "*.txt"));
        File file = fileChooser.showOpenDialog(primaryStage);

        try{
            BufferedReader bReader = new BufferedReader(new FileReader(file));   // lee el archivo .txt seleccionado
            readHotKeys(bReader);
        }catch (RuntimeException ignored){}
    }

    public void readHotKeys(BufferedReader bufferedReader) {
        try{
            bufferedReader.lines().forEach(line->{
                String[] split = line.split(":");
                String identifier = split[0];   String key = split[1];
                switch (identifier) {
                    case "HotKeyLeft":
                        Platform.runLater(() -> txtHotKeyLeft.setText(key));
                        break;
                    case "HotKeyRight":
                        Platform.runLater(() -> txtHotKeyRight.setText(key));
                        break;
                    case "HotKeyUp":
                        Platform.runLater(() -> txtHotKeyUp.setText(key));
                        break;
                    case "HotKeyDown":
                        Platform.runLater(() -> txtHotKeyDown.setText(key));
                        break;
                    case "HotKeyUpperRight":
                        Platform.runLater(() -> txtHotKeyUpperRight.setText(key));
                        break;
                    case "HotKeyUpperLeft":
                        Platform.runLater(() -> txtHotKeyUpperLeft.setText(key));
                        break;
                    case "HotKeyLowerRight":
                        Platform.runLater(() -> txtHotKeyLowerRight.setText(key));
                        break;
                    case "HotKeyLowerLeft":
                        Platform.runLater(() -> txtHotKeyLowerLeft.setText(key));
                        break;
                }
            });
        }catch (RuntimeException runtimeException){
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(primaryStage);
            // double xWindows = primaryStage.getX();      double yWindows = primaryStage.getY(); // Obtiene (x,y) de la ventana principal
            // alert.setX(xWindows);   alert.setY(yWindows - 230.0); // Cambia la posicion (x,y) del alert
            alert.setHeaderText("We are sorry :(");
            alert.setOnShowing(event -> alert.setContentText("Error loading the configuration, the format could be wrong."));
            alert.show();
        }
    }

    /*public void notepadexecute(String command) {
        try {
            Runtime.getRuntime().exec(command);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

}

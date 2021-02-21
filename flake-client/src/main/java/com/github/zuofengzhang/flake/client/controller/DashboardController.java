package com.github.zuofengzhang.flake.client.controller;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.github.zuofengzhang.flake.client.constraints.FlakeLabel;
import com.github.zuofengzhang.flake.client.constraints.FlakeSettings;
import com.github.zuofengzhang.flake.client.entity.*;
import com.github.zuofengzhang.flake.client.service.TaskService;
import com.github.zuofengzhang.flake.client.utils.DateUtils;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventTarget;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import net.rgielen.fxweaver.core.FxControllerAndView;
import net.rgielen.fxweaver.core.FxWeaver;
import net.rgielen.fxweaver.core.FxmlView;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author zhangzuofeng1
 */
@Component
@FxmlView("dashboard.fxml")
public class DashboardController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    public TitledPane titledPane;

    public Accordion according;
    public Button stopButton;
    public Label timerStatsLabel;
    public Label timerCounterLabel;
    public ComboBox<String> typeComboBox;
    public TextField newContentTextField;
    public Button addButton;
    public ListView<TaskDto> yesterdayList;
    public ListView<TaskDto> todayPlanList;
    public ListView<TaskDto> todayTomatoList;
    public ListView<TaskDto> summaryList;
    public TitledPane yesterdayTitledPane;
    public TitledPane todayPlanTitledPane;
    public TitledPane tomatoPotatoTitledPane;
    public TitledPane todaySummaryTitledPane;
    public TextField mottoTextField;
    public BorderPane datePickerPane;
    public Label workContentLabel;
    public TitledPane undoneTitledPane;
    public ListView<TaskDto> undoneList;
    @Resource
    private TaskService taskService;
    private DatePicker datePicker;
    private int currentTaskId = -1;
    //
    //    private AudioClip mNotify;
    //
    private Map<Integer, TitledPane> titledPaneMap;
    private Map<Integer, ListView<TaskDto>> listViewMap;
    private Timeline timeline;
    @Resource
    private SettingsController settingsController;
    private Consumer<ActionEvent> o;

    @Resource
    private FxWeaver fxWeaver;


    public void onNewContentKeyPressed(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.ENTER) {
            doAddNewTask();
        }
    }

    private void doAddNewTask() {
        String text = newContentTextField.getText();
        log.info("newContentTextField: {}", text);
        if (StringUtils.isNotBlank(text)) {
            // get selected dayId
            LocalDate localDate = datePicker.getValue();
            int dayId = DateUtils.dayId(localDate);
            // get taskType
            TaskType taskType = TaskType.findByCName(typeComboBox.getSelectionModel().getSelectedItem());
            assert taskType != null;
            int taskTypeId = taskType.getCId();
            //
            TaskDto taskDto = TaskDto.builder()
                    .dayId(dayId)
                    .taskType(taskType)
                    .title(text)
                    .content("")
                    .createdTime(System.currentTimeMillis())
                    .updateTime(System.currentTimeMillis())
                    .importanceUrgencyAxis(4)
                    .finished(false)
                    .storeStatus(StoreStatus.YES)
                    .build();
            taskService.insert(taskDto);
            // bind db action
            onTaskDataChange(taskDto);

            // expand selected TitledPane
            BooleanProperty expandedProperty = titledPaneMap.get(taskTypeId).expandedProperty();
            if (expandedProperty.get()) {
                reloadCurrentTitlePane();
            }
            expandedProperty.set(true);
            //
//            newContentTextField.clear();
        }
    }


    private void onTaskDataChange(TaskDto taskDto) {
        log.info("onDataChange: {}", taskDto.getTaskId());
        taskDto.finishedProperty().addListener((observableValue, s, t1) -> {
            log.info("update finished status:  @{},{}->{}", taskDto.getTaskId(), s, t1);
            taskService.updateById(taskDto);
        });
        taskDto.iuaProperty().addListener(((observableValue, s, t1) -> {
            log.info("update iua value:  @{},{}->{}", taskDto.getTaskId(), s, t1);
            taskService.updateById(taskDto);
        }));
        taskDto.attachmentProperty().addListener((observableValue, s, t1) -> {
            log.info("update attachment: @{},{}->{}", taskDto.getTaskId(), s, t1);
            taskService.updateById(taskDto);
        });
        taskDto.titleProperty().addListener((observableValue, s, t1) -> {
            log.info("update title: @{},{}->{}", taskDto.getTaskId(), s, t1);
            taskService.updateById(taskDto);
        });
        taskDto.contentProperty().addListener((observableValue, s, t1) -> {
            log.info("update content: @{},{}->{}", taskDto.getTaskId(), s, t1);
            taskService.updateById(taskDto);
        });
    }

    public void onAddButtonAction(ActionEvent actionEvent) {
        doAddNewTask();
    }

    public void onMoveMenu(ActionEvent actionEvent) {
        MenuItem eventSource = (MenuItem) actionEvent.getTarget();
        int targetId = Integer.parseInt(eventSource.getId());
        ContextMenu popup = eventSource.getParentMenu().getParentPopup();
        int sourceId = Integer.parseInt(popup.getId());
        ListView<TaskDto> listView = listViewMap.get(sourceId);
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        selectedItem.setTaskType(TaskType.findById(targetId));
        if (taskService.updateById(selectedItem) > 0) {
            listView.getItems().remove(selectedItem);
//            listViewMap.get(targetId).getItems().add(selectedItem);
        }
    }

    public void onDeleteMenu(ActionEvent actionEvent) {
        EventTarget target = actionEvent.getTarget();
        MenuItem menuItem = (MenuItem) target;
        ContextMenu parentPopup = menuItem.getParentMenu().getParentPopup();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(parentPopup.getId()));
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            taskService.deleteById(selectedItem);
            listView.getItems().remove(selectedItem);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // init UI & events
        // init
//        mNotify = new AudioClip(getClass().getResource("/sounds/notify.mp3").toExternalForm());

        // datepicker
        datePicker = new DatePicker(LocalDate.now());
        DatePickerSkin datePickerSkin = new DatePickerSkin(datePicker);
        Node popupContent = datePickerSkin.getPopupContent();
        datePickerPane.setCenter(popupContent);

        // type
        List<String> taskTypeNames
                = Arrays
                .stream(TaskType.values())
                .map(TaskType::getCname)
                .collect(Collectors.toList());
        typeComboBox.getItems().addAll(taskTypeNames);
        typeComboBox.getSelectionModel().select(0);
        typeComboBox.getSelectionModel().selectedItemProperty().addListener((observableValue, s, t1) -> {
            if (!s.equals(t1)) {
                titledPaneMap.get(Objects.requireNonNull(TaskType.findByCName(t1)).getCId())
                        .expandedProperty().setValue(true);
            }
        });
        //
        addButton.requestFocus();


        //
        List<ListView<TaskDto>> listViewList = Arrays.asList(yesterdayList, todayPlanList, todayTomatoList, summaryList, undoneList);
        listViewMap = listViewList.stream().collect(Collectors.toMap(s -> Integer.parseInt(s.getId()), s -> s));
        // listViewCellFactory
        yesterdayList.setCellFactory(t -> new TaskCell());
        todayPlanList.setCellFactory(t -> new TaskCell());
        todayTomatoList.setCellFactory(t -> new TaskCell());
        summaryList.setCellFactory(t -> new TaskCell());
        undoneList.setCellFactory(t -> new TaskCell());
        //
        titledPaneMap = Stream.of(yesterdayTitledPane, todayPlanTitledPane, tomatoPotatoTitledPane, todaySummaryTitledPane)
                .collect(Collectors.toMap(s -> Integer.parseInt(s.getId()), s -> s));
        // 修改为: 点击展开时，重新加载；如何清理掉事件绑定?
        Stream.of(yesterdayTitledPane, todayPlanTitledPane, tomatoPotatoTitledPane, todaySummaryTitledPane, undoneTitledPane)
                .forEach(tp -> tp.expandedProperty().addListener((observableValue, aBoolean, newValue) -> {
                    int tpId = Integer.parseInt(tp.getId());
                    if (newValue) {
                        loadTitledPaneData(tpId);
                    } else {
                        clearTitledPaneData(tpId);
                    }
                }));

        // load data
//        loadData();
        // datePick action
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            onDatePickerChanged(oldValue, newValue);
        });

        // init timer
        setTimerText(0);
        setTimerStatus(FlakeLabel.BREAKING);
        setTimerContent("");
        stopButton.setVisible(false);

        // init view
        // expanded undoneTitledPane
        according.setExpandedPane(undoneTitledPane);

        // loadData
        loadTitledPaneData(Integer.parseInt(undoneTitledPane.getId()));
    }

    private void clearTitledPaneData(int titledPaneId) {
        TaskType taskType = TaskType.findById(titledPaneId);
        // undone
        if (taskType == null) {
            undoneList.getItems().forEach(task -> {
                task.finishedProperty().unbind();
                task.iuaProperty().unbind();
            });
            undoneList.getItems().clear();
        } else {
            // loadDayTask
            ListView<TaskDto> listView = listViewMap.get(titledPaneId);
            ObservableList<TaskDto> items = listView.getItems();
            items.forEach(task -> {
                task.finishedProperty().unbind();
                task.iuaProperty().unbind();
            });
            items.clear();
        }
    }

    private void loadTitledPaneData(int titledPaneId) {
        TaskType taskType = TaskType.findById(titledPaneId);
        int dayId = DateUtils.dayId(datePicker.getValue());
        titledPane.setText(FlakeLabel.CURRENT_DAY + " " + dayId);
        // undone
        if (taskType == null) {
            List<TaskDto> undoneTasks = taskService.findAllUndoneTasks();
//            undoneList.getItems().forEach(task -> {
//                task.finishedProperty().unbind();
//                task.iuaProperty().unbind();
//            });
//            undoneList.getItems().clear();
            if (!CollectionUtils.isEmpty(undoneTasks)) {
                undoneTasks.forEach(this::onTaskDataChange);
                undoneList.getItems().addAll(undoneTasks);
            }
        } else {
            // loadDayTask

            List<TaskDto> tasks = taskService.findTasksByDayIdAndType(dayId, taskType);
            ListView<TaskDto> listView = listViewMap.get(titledPaneId);
            ObservableList<TaskDto> items = listView.getItems();
//            items.forEach(task -> {
//                task.finishedProperty().unbind();
//                task.iuaProperty().unbind();
//            });
//            items.clear();
            tasks.forEach(this::onTaskDataChange);
            items.addAll(tasks);
        }
    }

    private void onDatePickerChanged(LocalDate oldValue, LocalDate newValue) {
        if (oldValue == newValue) {
            return;
        }

        reloadCurrentTitlePane();
//        loadData();
    }

    private void setTimerContent(String s) {
        this.workContentLabel.setText(s);
    }

    public void setTimerText(long remainingSeconds) {
        int hours = (int) (remainingSeconds / 60 / 60);
        int minutes = (int) ((remainingSeconds / 60) % 60);
        int seconds = (int) (remainingSeconds % 60);

        //Show only minute and second if hour is not available
        if (hours <= 0) {
            setTimerText(String.format("%02d:%02d", minutes, seconds));
        } else {
            setTimerText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        }
    }

    private void setTimerText(String s) {
        timerCounterLabel.setText(s);
    }

//    private void loadData() {
//        // loadDayTask
//        int dayId = DateUtils.dayId(datePicker.getValue());
//        titledPane.setText(FlakeLabel.CURRENT_DAY + " " + dayId);
//        List<TaskDto> list = taskService.findAllTasksByDayId(dayId);
//
//        List<TaskDto> needObserver = new ArrayList<>();
//        if (CollectionUtils.isNotEmpty(list)) {
//            Map<TaskType, List<TaskDto>> map = list.stream().collect(Collectors.groupingBy(TaskDto::getTaskType));
//
//            for (Map.Entry<TaskType, List<TaskDto>> entry : map.entrySet()) {
//                ObservableList<TaskDto> items = listViewMap.get(entry.getKey().getCId()).getItems();
//                items.clear();
//                List<TaskDto> dtos = entry.getValue();
//                items.addAll(dtos);
//                needObserver.addAll(dtos);
//            }
//        } else {
//            listViewMap.values().forEach(l -> l.getItems().clear());
//        }
//        // load all undone tasks
//        List<TaskDto> undoneTasks = taskService.findAllUndoneTasks();
//        needObserver.addAll(undoneTasks);
//        undoneList.getItems().addAll(undoneTasks);
//        //
//        needObserver.forEach(this::onDataChange);
////        taskService.findAllTasksByDayId()
//    }

    public void onAddMottoTextField(ActionEvent actionEvent) {
        mottoTextField.setText("");
    }

    public void initTimerAction(TimerActionType type) {

        TimerStatus timerStatus = new TimerStatus(type, System.currentTimeMillis());

        setTimerStatus(timerStatus.getType().getDisplayName());
        if (timerStatus.getType() == TimerActionType.FOCUS) {
            timerStatus.setRemainingSeconds(FlakeSettings.getInstance().getFocusTimeInSeconds());
        } else if (timerStatus.getType() == TimerActionType.BREAK) {
        }
        setTimerText(timerStatus.getRemainingSeconds());
        timeline = new Timeline();
        timeline.setCycleCount((int) timerStatus.getRemainingSeconds());
        timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), event -> {
            timerStatus.countDown();
            setTimerText(timerStatus.getRemainingSeconds());
        }));

        timeline.setOnFinished(event -> {
//            mNotify.play();
            Notifications.create().title(FlakeLabel.TIME_TO_WEAK).text("").hideAfter(Duration.minutes(5)).showWarning();
            doAddNewWorkLog(timerStatus);
            currentTaskId = -1;
            if (timerStatus.getType() == TimerActionType.FOCUS) {
                takeBreakNotification();
            }
            initTimerAction(timerStatus.getType() == TimerActionType.FOCUS ?
                    TimerActionType.BREAK : TimerActionType.FOCUS);
            stopButton.setVisible(false);
        });
    }

    private void doAddNewWorkLog(TimerStatus timerStatus) {
        int taskId = currentTaskId;
        log.info("add work log : {}", taskId);
        TaskDto taskDto = taskService.findById(taskId);
        TaskDto newTask = TaskDto.builder()
                .title(taskDto.getTitle())
                .content(taskDto.getContent())
                .taskType(TaskType.TOMATO_POTATO)
                .endTime(System.currentTimeMillis())
                .startTime(timerStatus.getStartTime())
                .dayId(taskDto.getDayId())
                .fullTomato(true)
                .build();
        taskService.insert(newTask);
        // how get the newest id
        listViewMap.get(TaskType.TOMATO_POTATO.getCId()).getItems().add(newTask);
    }

    /**
     * 休息结束
     */
    public void takeBreakNotification() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        // Get the Stage.
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        // Add a custom icon.
//        stage.getIcons().add(new Image(this.getClass().getResource("/images/icon.png").toString()));
        alert.setTitle("Information Dialog");
        alert.setHeaderText("Great! Now Take a Break");
        alert.setContentText("You have worked 30 min long. Now you should take a at least 5 minutes break to relax yourself.");

        alert.show();
        //alert.showAndWait();
        System.out.println("take a break notification");
    }

    public void onStartTimer(ActionEvent actionEvent) {
        ContextMenu parentPopup = ((MenuItem) actionEvent.getTarget()).getParentPopup();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(parentPopup.getId()));
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            return;
        }
        //
        int newTaskId = selectedItem.getTaskId();
        //
        stopButton.setVisible(true);
        //
        if (currentTaskId == newTaskId) {
            log.info("the same task is running");
            return;
        }
        if (currentTaskId != -1) {
            log.info("stop unfinished task: {}, start new task: {}", currentTaskId, newTaskId);
            if (timeline.getStatus() == Animation.Status.RUNNING) {
                log.info("stop timeLine..");
                timeline.stop();
                initTimerAction(TimerActionType.FOCUS);
            }
        } else {
            initTimerAction(TimerActionType.FOCUS);
        }

        this.currentTaskId = newTaskId;
        timeline.play();
        stopButton.setVisible(true);
        getTimerStatus();
        setTimerContent(selectedItem.getTitle());
    }

    //debugging purpose
    private Animation.Status getTimerStatus() {
        Animation.Status mStatus = timeline.getStatus();
        System.out.println(mStatus);
        return mStatus;
    }

    private void setTimerStatus(String timeForABreak) {
        timerStatsLabel.setText(timeForABreak);
    }

    public void onStopTimer(ActionEvent actionEvent) {
        log.info("stop timer");
        currentTaskId = -1;
        timeline.stop();
        stopButton.setVisible(false);
        setTimerText(0);
        getTimerStatus();
        setTimerStatus(FlakeLabel.BREAKING);
        setTimerContent("");
    }

    public void onSettings(ActionEvent actionEvent) {
        Node node = (Node) actionEvent.getSource();
        Scene scene1 = node.getScene();
        Stage primaryStage = (Stage) scene1.getWindow();
        BorderPane borderPane = fxWeaver.loadView(SettingsController.class, resourceBundle);
        Scene scene = new Scene(borderPane);
        Stage stage = new Stage();
        stage.setResizable(false);
        stage.initOwner(primaryStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(scene);
        stage.showAndWait();

        reloadCurrentTitlePane();
    }

    private void reloadCurrentTitlePane() {
        int titledPaneId = Integer.parseInt(according.getExpandedPane().getId());
        clearTitledPaneData(titledPaneId);
        loadTitledPaneData(titledPaneId);
    }


    public void onSetIuaMenu(ActionEvent actionEvent) {
        EventTarget target = actionEvent.getTarget();
        RadioMenuItem menuItem = (RadioMenuItem) target;
        ContextMenu parentPopup = menuItem.getParentPopup();
        // undoneListView id is 0
        String id = parentPopup.getId();
        //
        int targetIuaId = Integer.parseInt(menuItem.getId());
        TaskDto selectedItem = undoneList.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            int iua = selectedItem.getIua();
            if (targetIuaId != iua) {
                selectedItem.setIua(targetIuaId);
                log.info("set iua : {} -> {} ,{}", iua, targetIuaId, selectedItem);
                reloadCurrentTitlePane();
            }
        }
    }

    @Resource
    private ResourceBundle resourceBundle;

    public void onTaskClicked(MouseEvent mouseEvent) {
        EventTarget target = mouseEvent.getTarget();
        TitledPane expandedPane = according.getExpandedPane();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(expandedPane.getId()));
        TaskDto selectedTask = listView.getSelectionModel().getSelectedItem();
        if (selectedTask != null) {
            if (mouseEvent.getClickCount() == 2) {
                Node node = (Node) mouseEvent.getSource();
                Scene nodeScene = node.getScene();
                Stage primaryStage = (Stage) nodeScene.getWindow();
                FxControllerAndView<TaskDetailController, GridPane> controllerAndView = fxWeaver.load(TaskDetailController.class, resourceBundle);
                GridPane borderPane = controllerAndView.getView().get();
                TaskDetailController controller = controllerAndView.getController();
                controller.setData(selectedTask);
                Scene scene = new Scene(borderPane);
                Stage stage = new Stage();
                stage.setResizable(true);
                stage.initOwner(primaryStage);
                stage.setTitle(FlakeLabel.TASK_EDIT);
                stage.setScene(scene);
                stage.show();
            }
        }
    }

    public void onUndeleteMenu(ActionEvent actionEvent) {
        EventTarget target = actionEvent.getTarget();
        MenuItem menuItem = (MenuItem) target;
        ContextMenu parentPopup = menuItem.getParentMenu().getParentPopup();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(parentPopup.getId()));
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            taskService.restoreById(selectedItem);
//            listView.getItems().remove(selectedItem);
        }
    }

    public void onOrderMoveTopMenu(ActionEvent actionEvent) {
        EventTarget target = actionEvent.getTarget();
        MenuItem menuItem = (MenuItem) target;
        ContextMenu parentPopup = menuItem.getParentMenu().getParentPopup();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(parentPopup.getId()));
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            taskService.moveOrderTop(selectedItem);
            reloadCurrentTitlePane();
//            listView.getItems().remove(selectedItem);
        }
    }

    public void onOrderMoveUpMenu(ActionEvent actionEvent) {
        EventTarget target = actionEvent.getTarget();
        MenuItem menuItem = (MenuItem) target;
        ContextMenu parentPopup = menuItem.getParentMenu().getParentPopup();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(parentPopup.getId()));
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            taskService.moveOrderUp(selectedItem);
            reloadCurrentTitlePane();
//            listView.getItems().remove(selectedItem);
        }
    }

    public void onOrderMoveDownMenu(ActionEvent actionEvent) {
        EventTarget target = actionEvent.getTarget();
        MenuItem menuItem = (MenuItem) target;
        ContextMenu parentPopup = menuItem.getParentMenu().getParentPopup();
        ListView<TaskDto> listView = listViewMap.get(Integer.parseInt(parentPopup.getId()));
        TaskDto selectedItem = listView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            taskService.moveOrderDown(selectedItem);
            reloadCurrentTitlePane();
//            listView.getItems().remove(selectedItem);
        }
    }
}

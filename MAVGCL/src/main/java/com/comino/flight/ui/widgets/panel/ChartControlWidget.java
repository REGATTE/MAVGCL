/****************************************************************************
 *
 *   Copyright (c) 2017,2020 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.ui.widgets.panel;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.file.FileHandler;
import com.comino.flight.file.KeyFigurePreset;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.ui.widgets.charts.IChartControl;
import com.comino.jfx.extensions.ChartControlPane;
import com.comino.mavcom.control.IMAVController;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;

public class ChartControlWidget extends ChartControlPane  {

	private static final Integer[] TOTAL_TIME = { 15, 30, 60, 120, 240, 480 };


	@FXML
	private ChoiceBox<Integer> totaltime;

	@FXML
	private ComboBox<String> keyfigures;

	@FXML
	private Slider scroll;

	@FXML
	private Button save;

	//	@FXML
	//	private Button replay;

	@FXML
	private Button play;

	protected int totalTime_sec = TOTAL_TIME[0];
	private AnalysisModelService modelService;

	private StateProperties state = StateProperties.getInstance();

	private int   replay_index = 0;
	private long  replay_tms   = 0;
	private long  anim_tms     = 0;


	private AnimationTimer task;

	private Map<Integer,KeyFigurePreset> presets = new HashMap<Integer,KeyFigurePreset>();


	private long scroll_tms;

	public ChartControlWidget() {
		super(300,true);
		FXMLLoadHelper.load(this, "ChartControlWidget.fxml");

	}

	@FXML
	private void initialize() {

		this.modelService =  AnalysisModelService.getInstance();
		totaltime.getItems().addAll(TOTAL_TIME);
		totaltime.getSelectionModel().select(1);

		buildKeyfigureModelSelection();

		state.getLogLoadedProperty().addListener((o,ov,nv) -> {

			if(nv.booleanValue()) {
				state.getReplayingProperty().set(false);
				if(modelService.getModelList().size() < totalTime_sec * 1000 /  modelService.getCollectorInterval_ms() || modelService.isCollecting())
					scroll.setDisable(true);
				else
					scroll.setDisable(false);
				scroll.setValue(0);
			}

			for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
				if(chart.getValue().getTimeFrameProperty()!=null) {
					chart.getValue().getTimeFrameProperty().set(0);
					chart.getValue().getTimeFrameProperty().set(totaltime.valueProperty().get());
				}
			}

		});

		totaltime.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			totalTime_sec  = newValue.intValue();
			modelService.setTotalTimeSec(totalTime_sec);
			//scroll.setValue(1.0);

			if(state.getReplayingProperty().get()) {
				task.stop();
				state.getProgressProperty().set(-1);
				state.getReplayingProperty().set(false);
				state.getCurrentUpToDate().set(true);
			}

			for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
				if(chart.getValue().getTimeFrameProperty()!=null)
					chart.getValue().getTimeFrameProperty().set(newValue.intValue());
			}


			if(modelService.getModelList().size() < totalTime_sec * 1000 /  modelService.getCollectorInterval_ms()
					|| modelService.isCollecting() || modelService.getModelList().size()==0)
				scroll.setDisable(true);
			else
				scroll.setDisable(false);
		});

		scroll.setSnapToTicks(false);
		scroll.setSnapToPixel(false);
		scroll.setDisable(true);
		

		scroll.onMousePressedProperty().addListener((o,ov,nv) ->{
			state.getCurrentUpToDate().set(false);
		});
		
		scroll.onMouseReleasedProperty().addListener((o,ov,nv) ->{
			state.getCurrentUpToDate().set(true);
		});
		
		scroll.valueProperty().addListener((observable, oldvalue, newvalue) -> {
			if(state.getReplayingProperty().get() || (System.currentTimeMillis() - anim_tms) < 50)
				return;
			anim_tms = System.currentTimeMillis();

			final float v = (float)scroll.getValue();	
			if(!modelService.isCollecting() && !modelService.isReplaying()  && !state.getConnectedProperty().get() ) {
				modelService.setCurrent(modelService.calculateIndexByFactor(1f-v)+1);
			}
			charts.entrySet().forEach((chart) -> {
				if(chart.getValue().getScrollProperty()!=null && chart.getValue().isVisible()) {
					chart.getValue().getScrollProperty().set(1f-v);
				}
			});
		});

		scroll.valueChangingProperty().addListener((observable, oldvalue, newvalue) -> {
			if(state.getReplayingProperty().get() || (System.currentTimeMillis() - anim_tms) < 50)
				return;
			anim_tms = System.currentTimeMillis();
			charts.entrySet().forEach((chart) -> {
				if(chart.getValue().getIsScrollingProperty()!=null)
					chart.getValue().getIsScrollingProperty().set(newvalue.booleanValue());
			});
		});
		

		state.getRecordingProperty().addListener((observable, oldvalue, newvalue) -> {
			if(newvalue.intValue()!=AnalysisModelService.STOPPED) {
				state.getReplayingProperty().set(false);
				scroll.setDisable(true);
				scroll.setValue(0);
				return;
			}

			if(modelService.getModelList().size() < totalTime_sec * 1000 / modelService.getCollectorInterval_ms())
				scroll.setDisable(true);
			else
				scroll.setDisable(false);
		});

		scroll.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent click) {
				if(state.getReplayingProperty().get())
					return;
				if (click.getClickCount() == 2)
					scroll.setValue(scroll.getValue() == 1 ? 0 : 1);

			}
		});

		save.setOnAction((ActionEvent event)-> {
			saveKeyFigureSelection();
			event.consume();
		});

		play.disableProperty().bind(state.getRecordingProperty().isNotEqualTo(AnalysisModelService.STOPPED)
				.or(state.getRecordingAvailableProperty().not()
						.and(state.getLogLoadedProperty().not())));

		task = new AnimationTimer() {
			long replay_time_ms; long replay_index_old;
			@Override public void handle(long now) {


				anim_tms = System.currentTimeMillis();

				if(replay_index < modelService.getModelList().size()) {

					replay_time_ms = System.currentTimeMillis() -  replay_tms;

					charts.entrySet().forEach((chart) -> { 
						if(chart.getValue().getReplayProperty()!=null)
							chart.getValue().getReplayProperty().set(replay_index);
					});
					replay_index = (int)(replay_time_ms / modelService.getCollectorInterval_ms());
					if(replay_index > replay_index_old) {
						state.getProgressProperty().set((float)(replay_index) / modelService.getModelList().size() );
						scroll.setValue((1f - (float)replay_index/modelService.getModelList().size()));
					} 
					replay_index_old = replay_index;
					modelService.setCurrent(replay_time_ms/1000f);


				} else {
					task.stop();
					scroll.setValue(0);
					state.getProgressProperty().set(-1);
					state.getReplayingProperty().set(false);
					state.getCurrentUpToDate().set(true);
				}
			}
		};

		play.setOnAction((ActionEvent event)-> {
			if(!state.getReplayingProperty().get() && modelService.getModelList().size() > 0) {
				state.getReplayingProperty().set(true);
				state.getCurrentUpToDate().set(false);

				if(scroll.getValue()<0.05)
					scroll.setValue(1);

				replay_index = ((int)(modelService.getModelList().size() * (1f - scroll.getValue())))+1;

				charts.entrySet().forEach((chart) -> { 
					if(chart.getValue().getReplayProperty()!=null)
						chart.getValue().getReplayProperty().set(-replay_index);
				});

				replay_tms = System.currentTimeMillis() - (long)(replay_index * modelService.getCollectorInterval_ms());
				anim_tms = 0;
				task.start();
			} else {
				task.stop();
				state.getProgressProperty().set(-1);
				state.getReplayingProperty().set(false);
				state.getCurrentUpToDate().set(true);	
			}
			event.consume();
		});

		state.getReplayingProperty().addListener((e,o,n) -> {

			Platform.runLater(() -> {
				if(n.booleanValue() ) {
					//	play.setText("\u25A0");
					play.setText("||");
					scroll.setDisable(true);
				}
				else {
					state.getProgressProperty().set(-1);
					replay_index = 0;
					play.setText("\u25B6");
					scroll.setDisable(false);
				}
			});
		});

	}


	public void setup(IMAVController control, StatusWidget statuswidget) {
		this.control = control;
		this.modelService =  AnalysisModelService.getInstance();
		this.modelService.setTotalTimeSec(totalTime_sec);
		this.modelService.clearModelList();

		Platform.runLater(() -> {
			for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
				if(chart.getValue().getTimeFrameProperty()!=null)
					chart.getValue().getTimeFrameProperty().set(totalTime_sec);
				if(chart.getValue().getScrollProperty()!=null)
					chart.getValue().getScrollProperty().set(1);
			}
		});
	}

	public void refreshCharts() {
		super.refreshCharts();
		scroll.setValue(0);
		if(modelService.getModelList().size() > totalTime_sec * 1000 /  modelService.getCollectorInterval_ms())
			scroll.setDisable(false);
	}

	private void saveKeyFigureSelection() {
		for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
			presets.put(chart.getKey(), chart.getValue().getKeyFigureSelection());
		}
		FileHandler.getInstance().presetsExport(presets);
	}


	private void buildKeyfigureModelSelection() {

		keyfigures.getItems().add("Select presets...");
		keyfigures.getItems().add("Open...");

		keyfigures.setVisibleRowCount(FileHandler.getInstance().getPresetList().size()+2);

		for(String p : FileHandler.getInstance().getPresetList())
			keyfigures.getItems().add(p);

		keyfigures.setEditable(true);
		keyfigures.getEditor().setEditable(false);
		keyfigures.getEditor().setCursor(Cursor.DEFAULT);
		keyfigures.getSelectionModel().select(0);

		keyfigures.getSelectionModel().selectedIndexProperty().addListener((o,ov,nv) -> {
			Map<Integer,KeyFigurePreset> pr = null;

			switch(nv.intValue()) {
			case 0:
				break;
				//			case 1:
				//				for(Entry<Integer, KeyFigurePreset> preset : presets.entrySet()) {
				//					charts.get(preset.getKey()).setKeyFigureSeletcion(preset.getValue());
				//				}
				//				break;
			case 1:
				pr = FileHandler.getInstance().presetsImport(null);
				if(pr != null) {
					this.presets.clear(); this.presets.putAll(pr);
					for(Entry<Integer, KeyFigurePreset> preset : presets.entrySet()) {
						charts.get(preset.getKey()).setKeyFigureSelection(preset.getValue());
					}
				}
				break;
			default:
				pr = FileHandler.getInstance().presetsImport(keyfigures.getItems().get(nv.intValue()));
				if(pr != null) {
					this.presets.clear(); this.presets.putAll(pr);
					for(Entry<Integer, KeyFigurePreset> preset : presets.entrySet()) {
						charts.get(preset.getKey()).setKeyFigureSelection(preset.getValue());
					}
				}
				break;

			}
			Platform.runLater(() -> {
				keyfigures.getSelectionModel().select(0);
			});
		});

	}

}

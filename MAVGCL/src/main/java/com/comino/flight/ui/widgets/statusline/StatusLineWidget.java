/****************************************************************************
 *
 *   Copyright (c) 2017,2018 Eike Mansfeld ecm@gmx.de. All rights reserved.
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

package com.comino.flight.ui.widgets.statusline;

import java.io.IOException;
import java.util.List;

import org.mavlink.messages.ESTIMATOR_STATUS_FLAGS;

import com.comino.flight.MainApp;
import com.comino.flight.base.UBXRTCM3Base;
import com.comino.flight.file.FileHandler;
import com.comino.flight.file.KeyFigurePreset;
import com.comino.flight.model.AnalysisDataModel;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.flight.model.service.ICollectorRecordingListener;
import com.comino.flight.observables.StateProperties;
import com.comino.flight.ui.widgets.charts.IChartControl;
import com.comino.flight.ui.widgets.panel.ChartControlWidget;
import com.comino.jfx.extensions.Badge;
import com.comino.mavcom.control.IMAVController;
import com.comino.mavcom.control.impl.MAVController;
import com.comino.mavcom.model.DataModel;
import com.comino.mavcom.model.segment.Status;
import com.comino.mavcom.model.segment.Vision;
import com.comino.speech.VoiceTTS;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.FloatProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleFloatProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class StatusLineWidget extends Pane implements IChartControl {

	@FXML
	private Badge ready;

	@FXML
	private Badge driver;

	@FXML
	private Badge time;

	@FXML
	private Badge mode;

	@FXML
	private Badge rc;

	@FXML
	private Badge controller;

	@FXML
	private Badge gps;

	@FXML
	private Badge ekf;

	@FXML
	private Badge vision;

	@FXML
	private Badge fps;

	@FXML
	private Badge gpos;

	@FXML
	private Badge lpos;

	@FXML
	private Badge wp;


	private IMAVController control;

	private FloatProperty scroll       = new SimpleFloatProperty(0);
	private FloatProperty replay       = new SimpleFloatProperty(0);

	private AnalysisModelService collector = AnalysisModelService.getInstance();
	private StateProperties state = null;

	private String filename;

	private final static String[]  EKF2STATUS_TEXTS = { "", "ATT", "RPOS", "APOS", "FAULT", "VEL", "OTHER"  };

	private AnimationTimer task = null;

	int current_x0_pt = 0; int current_x1_pt = 0;
	long last = 0;

	private DataModel model;

	public StatusLineWidget() {
		FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("StatusLineWidget.fxml"));
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);
		try {
			fxmlLoader.load();
		} catch (IOException exception) {

			throw new RuntimeException(exception);
		}

		task = new AnimationTimer() {


			@Override
			public void handle(long now) {

				if((now - last) < 200000000)
					return;
				last = now;

				int ekf_status = getEKF2Status();
				List<AnalysisDataModel> list = null;

				if(model.slam.wpcount > 0) {
					wp.setText(String.format("WP %d", model.slam.wpcount));
					wp.setMode(Badge.MODE_ON);
				}
				else if(model.sys.t_takeoff_ms < 0 ) {
					wp.setText(String.format("T % d", (int)(model.sys.t_takeoff_ms/1000-0.5f)));
					wp.setMode(Badge.MODE_ON);
				}
				else {
					wp.setText("");
					wp.setMode(Badge.MODE_OFF);
				}

				if(UBXRTCM3Base.getInstance()!=null && UBXRTCM3Base.getInstance().getSVINStatus().get()) {
					gps.setMode(Badge.MODE_ON);
					gps.setText("SVIN");
				} else {
					if(!control.isConnected() || !model.sys.isSensorAvailable(Status.MSP_GPS_AVAILABILITY))
						gps.setMode(Badge.MODE_OFF);
					else {
						switch(model.gps.fixtype & 0xF) {

						case 2:
							gps.setMode(Badge.MODE_ON);
							gps.setText("GPS");
						case 3:
							gps.setMode(Badge.MODE_ON);
							gps.setText("GPS Fix");
							break;
						case 4:
							gps.setMode(Badge.MODE_ON);
							gps.setText("GPS 3D");
							break;
						case 5:
							gps.setMode(Badge.MODE_ON);
							gps.setText("DGPS");
							break;
						case 6:
							gps.setMode(Badge.MODE_ON);
							gps.setText("RTK float");
							break;
						case 7:
							gps.setMode(Badge.MODE_ON);
							gps.setText("RTK fixed");
							break;

						default:
							gps.setText("No Fix");
							gps.setMode(Badge.MODE_OFF);
						}
					}
				}

				filename = FileHandler.getInstance().getName();
				driver.setText(model.sys.getSensorString());
				vision.setText(model.vision.getShortText());

				if(control.isConnected()) {

					if(model.sys.isSensorAvailable(Status.MSP_IMU_AVAILABILITY))
						driver.setBackgroundColor(Color.web("#1c6478"));
					driver.setMode(Badge.MODE_ON);
					if(model.vision.isStatus(Vision.PUBLISHED))
						vision.setMode(Badge.MODE_ON);
					else
						vision.setMode(Badge.MODE_OFF);

					if(model.sys.isSensorAvailable(Status.MSP_MSP_AVAILABILITY)) {
						ready.setMode(Badge.MODE_ON);
						if(model.sys.isStatus(Status.MSP_READY_FOR_FLIGHT)) {
							ready.setBackgroundColorWhiteText(Color.LIMEGREEN);
							ready.setText("READY");

						}
						else {
							ready.setBackgroundColorWhiteText(Color.RED);
							ready.setText("NOT READY");
						}
					} else {
						
						ready.setMode(Badge.MODE_ON);
						if(ekf_status != 4) {
							ready.setBackgroundColorWhiteText(Color.web("#1c6478"));
							ready.setText("");

						} else {
							ready.setBackgroundColorWhiteText(Color.RED);
							ready.setText("NOT READY");
						}
					}

				}
				else {
					ready.setMode(Badge.MODE_OFF);
					driver.setMode(Badge.MODE_OFF);
					ekf.setMode(Badge.MODE_OFF);
					vision.setMode(Badge.MODE_OFF);
					driver.setText("");
				}

				list = collector.getModelList();

				if(list.size()>0) {
					if(!state.getReplayingProperty().get()) {
						current_x0_pt = collector.calculateX0IndexByFactor(scroll.floatValue());
						current_x1_pt = collector.calculateX1IndexByFactor(scroll.floatValue());
					}

					if(current_x1_pt < list.size()-1)
						time.setText(
								String.format("%1$tM:%1$tS - %2$tM:%2$tS / %3$tM:%3$tS",
										list.get(current_x0_pt).tms/1000,
										list.get(current_x1_pt).tms/1000,
										list.get(list.size()-1).tms/1000)
								);
					else
						time.setText(
								String.format("%1$tM:%1$tS - %2$tM:%2$tS",
										list.get(current_x0_pt).tms/1000,
										list.get(current_x1_pt).tms/1000)
								);

					time.setBackgroundColor(Color.web("#1c6478"));
				} else {
					time.setText("00:00 - 00:00");
					time.setBackgroundColor(Color.GRAY);
				}

				if(state.getReplayingProperty().get()) {
					mode.setBackgroundColor(Color.web("#2989a3"));
					mode.setText("Replay");
					mode.setMode(Badge.MODE_ON);
				}
				else if(!filename.isEmpty()) {
					mode.setBackgroundColor(Color.web("#2989a3"));
					mode.setText(filename);
					mode.setMode(Badge.MODE_ON);
				}
				else if(control.isConnected()) {
					switch(control.getMode()) {
					case MAVController.MODE_NORMAL:
						mode.setText("Connected");
						break;
					case MAVController.MODE_USB:
						mode.setText("Serial");
						break;
					case MAVController.MODE_SITL:
						mode.setText("SITL");
						break;	
					case MAVController.MODE_SITL_PROXY:
						mode.setText("SITL Proxy");
						break;	
					}
					mode.setBackgroundColor(Color.web("#1c6478"));
					mode.setMode(Badge.MODE_ON);
				} else {
					mode.setMode(Badge.MODE_OFF);
				}

				if(list.size()==0) {
					time.setMode(Badge.MODE_OFF);
				} else {
					time.setMode(Badge.MODE_ON);
				}

				if(control.isConnected()) {
					ekf.setText(EKF2STATUS_TEXTS[ekf_status]);
					if(ekf_status != 4)
						ekf.setBackgroundColor(Color.web("#1c6478"));
					else
						ekf.setBackgroundColor(Color.DARKRED);
					ekf.setMode(Badge.MODE_ON);
				}

			}

		};


		driver.setAlignment(Pos.CENTER_LEFT);
	}

	public void setup(IMAVController control) {
		ChartControlWidget.addChart(99,this);
		this.control = control;
		this.model = control.getCurrentModel();
		this.state = StateProperties.getInstance();

		//	control.getStatusManager().addListener(Status.MSP_CONNECTED, (n) -> {
		state.getConnectedProperty().addListener((v,o,n) -> {
			driver.setDisable(!n.booleanValue());
			rc.setDisable(!n.booleanValue());
			gpos.setDisable(!n.booleanValue());
			lpos.setDisable(!n.booleanValue());
			controller.setDisable(!n.booleanValue());
			ekf.setDisable(!n.booleanValue());
			ready.setDisable(!n.booleanValue());

		});

		control.getStatusManager().addListener(Status.MSP_RC_ATTACHED, (n) -> {
			if((n.isStatus(Status.MSP_RC_ATTACHED)))
				rc.setMode(Badge.MODE_ON);
			else
				rc.setMode(Badge.MODE_OFF);
		});

		control.getStatusManager().addListener(Status.MSP_GPOS_VALID, (n) -> {
			if((n.isStatus(Status.MSP_GPOS_VALID)))
				gpos.setMode(Badge.MODE_ON);
			else
				gpos.setMode(Badge.MODE_OFF);
		});

		control.getStatusManager().addListener(Status.MSP_LPOS_VALID, (n) -> {
			if((n.isStatus(Status.MSP_LPOS_VALID)))
				lpos.setMode(Badge.MODE_ON);
			else
				lpos.setMode(Badge.MODE_OFF);
		});

		state.getControllerConnectedProperty().addListener((e,o,n) -> {
			if(n.booleanValue())
				controller.setMode(Badge.MODE_ON);
			else
				controller.setMode(Badge.MODE_OFF);
		});

		scroll.addListener((e,o,n) -> {
			current_x0_pt = collector.calculateX0IndexByFactor(n.floatValue());
			current_x1_pt = collector.calculateX1IndexByFactor(n.floatValue());
		});


		replay.addListener((e,o,n) -> {
			if(n.intValue()>0) {
				current_x1_pt = n.intValue();
				current_x0_pt = collector.calculateX0Index(n.intValue());
			}
		});
		task.start();

	}

	@Override
	public FloatProperty getScrollProperty() {
		return scroll;
	}

	@Override
	public IntegerProperty getTimeFrameProperty() {
		return null;
	}

	public BooleanProperty getIsScrollingProperty() {
		return null;
	}

	@Override
	public FloatProperty getReplayProperty() {
		return replay;
	}

	@Override
	public void refreshChart() {

	}

	public void setKeyFigureSelection(KeyFigurePreset preset) {

	}

	public KeyFigurePreset getKeyFigureSelection() {
		return null;
	}

	private int getEKF2Status() {
		int flags = (int)model.est.flags;


		if(flags == 0
				|| (flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_ACCEL_ERROR)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_ACCEL_ERROR
				|| (flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_GPS_GLITCH)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_GPS_GLITCH) {
			return 4;
		}

		if((flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_POS_HORIZ_ABS)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_POS_HORIZ_ABS )
			return 3;
		else if((flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_PRED_POS_HORIZ_ABS)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_PRED_POS_HORIZ_ABS )
			return 3;
		else if ((flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_POS_HORIZ_REL)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_POS_HORIZ_REL )
			return 2;
		else if ((flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_PRED_POS_HORIZ_REL)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_PRED_POS_HORIZ_REL )
			return 2;
		else if ((flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_VELOCITY_HORIZ)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_VELOCITY_HORIZ )
			return 5;
		else if ((flags & ESTIMATOR_STATUS_FLAGS.ESTIMATOR_ATTITUDE)==ESTIMATOR_STATUS_FLAGS.ESTIMATOR_ATTITUDE )
			return 1;
		else
			return 6;

	}


}

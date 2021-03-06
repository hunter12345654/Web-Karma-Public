/*******************************************************************************
 * Copyright 2012 University of Southern California
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * 	http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code was developed by the Information Integration Group as part 
 * of the Karma project at the Information Sciences Institute of the 
 * University of Southern California.  For more information, publications, 
 * and related projects, please see: http://www.isi.edu/integration
 ******************************************************************************/

package edu.isi.karma.controller.command.cleaning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import org.apache.log4j.Level;
import org.geotools.resources.Java6;
import org.json.JSONObject;

import org.json.JSONArray;
import org.json.JSONException;
import org.apache.log4j.Logger;
import org.apache.log4j.FileAppender;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.SimpleLayout;


import edu.isi.karma.controller.command.AddColumnCommand;
import edu.isi.karma.controller.command.Command;
import edu.isi.karma.controller.command.CommandException;
import edu.isi.karma.controller.command.CommandFactory;
import edu.isi.karma.controller.command.JSONInputCommandFactory;
import edu.isi.karma.controller.command.MultipleValueEditColumnCommand;
import edu.isi.karma.controller.command.WorksheetCommand;
import edu.isi.karma.controller.history.WorksheetCommandHistoryReader;
import edu.isi.karma.controller.update.InfoUpdate;
import edu.isi.karma.controller.update.UpdateContainer;
import edu.isi.karma.rep.HNodePath;
import edu.isi.karma.rep.Node;
import edu.isi.karma.rep.Worksheet;
import edu.isi.karma.rep.cleaning.RamblerTransformationInputs;
import edu.isi.karma.rep.cleaning.RamblerTransformationOutput;
import edu.isi.karma.rep.cleaning.RamblerValueCollection;
import edu.isi.karma.rep.cleaning.TransformationExample;
import edu.isi.karma.rep.cleaning.ValueCollection;
import edu.isi.karma.view.VWorkspace;
import edu.isi.karma.webserver.ExecutionController;
import edu.isi.karma.webserver.KarmaException;
import edu.isi.karma.webserver.WorkspaceRegistry;

public class SubmitCleanningCommand extends WorksheetCommand{
	String hNodeId = "";
	String vWorksheetId = "";
	String hTableId = "";
	private static Logger logger =Logger.getLogger(SubmitCleanningCommand.class);
	private Vector<TransformationExample> examples = new Vector<TransformationExample>();
	protected SubmitCleanningCommand(String id, String worksheetId,String hNodeId, String hTableId, String vWorkSheetId,String Examples) {
		super(id, worksheetId);
		this.hNodeId = hNodeId;
		this.vWorksheetId = vWorkSheetId;
		this.hTableId = hTableId;
		this.examples = GenerateCleaningRulesCommand.parseExample(Examples);
		try
		{
			FileAppender appender = new FileAppender(new SimpleLayout(),"./log/cleanning.log");
			logger.addAppender(appender);
		}
		catch (Exception e) {

		}
	}

	@Override
	public String getCommandName() {
		return null;
	}

	@Override
	public String getTitle() {
		return null;
	}

	@Override
	public String getDescription() {
		return null;
	}

	@Override
	public CommandType getCommandType() {
		return CommandType.undoable;
	}

	public JSONArray creatNewColumnCommand(String worksheetId,String hTableId,String colname)
	{
		String cmdString = String.format("[{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
										"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
										"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
										"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
										"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
										"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"}]",
										"id",this.id,"vWorksheetId",this.vWorksheetId,
										"worksheetId",worksheetId,"hTableId",hTableId,
										"hNodeId",this.hNodeId,"newColumnName",colname);
		System.out.println(""+cmdString);
		JSONArray jsonArray = new JSONArray();
		try {
			jsonArray= new JSONArray(cmdString);
		} catch (Exception e) {
		}
		return jsonArray;
	}
	public JSONArray createMultiCellCmd(ValueCollection vc,String nHNodeId)
	{
		/*super(id);
		this.hNodeID = hNodeID;
		this.vWorksheetId = vWorksheetID;
		this.newRowValueMap = rowValueMap;*/
		JSONArray strData = new JSONArray();
		for(String key:vc.getNodeIDs())
		{
			String value = vc.getValue(key);
			JSONObject jsonObject;
			try {
				value = value.replaceAll("\"", "\\\\\"");
				jsonObject = new JSONObject(String.format("{\"rowID\":\"%s\",\"value\":\"%s\"}", key,value));
				strData.put(jsonObject);
			} catch (JSONException e) {
				logger.info(e.toString());
			}			
		}
		String cmdString = String.format("[{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
				"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
				"{\"name\":\"%s\",\"type\":\"other\",\"value\":\"%s\"},"+
				"{\"name\":\"%s\",\"type\":\"other\",\"value\":%s}]",
				"id",this.id,"hNodeID",nHNodeId,"vWorksheetID",this.vWorksheetId,"rows",strData.toString()
				);
		JSONArray cmdArray = new JSONArray();
		try {
			cmdArray = new JSONArray(cmdString);
		} catch (JSONException e) {
		}
		return cmdArray;
	}
	@Override
	public UpdateContainer doIt(VWorkspace vWorkspace) {
		// create new column command
		
		String worksheetId = vWorkspace.getViewFactory().getVWorksheet(this.vWorksheetId).getWorksheetId();
		String Msg = String.format("submit end, Time:%d, Worksheet:%s",System.currentTimeMillis()/1000,worksheetId);
		logger.info(Msg);
		String hTableId = "";
		String colnameString = "";
		try
		{
			// obtain transformed results
			HashMap<String, String> rows = new HashMap<String,String>();
			HNodePath selectedPath = null;
			Random r = new Random();
			int colno = r.nextInt(1000);
			List<HNodePath> columnPaths = vWorkspace.getRepFactory().getWorksheet(worksheetId).getHeaders().getAllPaths();
			for (HNodePath path : columnPaths) {
				if (path.getLeaf().getId().equals(hNodeId)) {
					hTableId = path.getLeaf().getHTableId();
					colnameString = path.getLeaf().getColumnName()+"_"+colno;
					selectedPath = path;
				}
			}
			Collection<Node> nodes = new ArrayList<Node>();
			vWorkspace.getRepFactory().getWorksheet(worksheetId).getDataTable().collectNodes(selectedPath, nodes);	
			for (Node node : nodes) {
				String id = node.getBelongsToRow().getId();
				String originalVal = node.getValue().asString();
				if(!rows.containsKey(id))
					rows.put(id, originalVal);
			}
			RamblerValueCollection vc = new RamblerValueCollection(rows);
			RamblerTransformationInputs inputs = new RamblerTransformationInputs(examples, vc);
			//generate the program
			boolean results = false;
			int iterNum = 0;
			RamblerTransformationOutput rtf = null;
			while(iterNum<1 && !results) // try to find any rule during 5 times running
			{
				rtf = new RamblerTransformationOutput(inputs);
				if(rtf.getTransformations().keySet().size()>0)
				{
					results = true;
				}
				iterNum ++;
			}	
			Iterator<String> iter = rtf.getTransformations().keySet().iterator();
			Vector<ValueCollection> vvc = new Vector<ValueCollection>();
			String tpid = iter.next();
			ValueCollection rvco = rtf.getTransformedValues(tpid);
			vvc.add(rvco);
			//add a new column
			JSONArray inputParamArr0 = this.creatNewColumnCommand(worksheetId,hTableId,colnameString);
			ExecutionController ctrl = WorkspaceRegistry.getInstance().getExecutionController(vWorkspace.getWorkspace().getId());
			CommandFactory cf0 = ctrl.getCommandFactoryMap().get(AddColumnCommand.class.getSimpleName());
			JSONInputCommandFactory scf1 = (JSONInputCommandFactory)cf0;
			Command comm1 = null;
			try {
				comm1 = scf1.createCommand(inputParamArr0, vWorkspace);
			} catch (JSONException e1) {
				e1.printStackTrace();
			} catch (KarmaException e1) {
				e1.printStackTrace();
			}
			if(comm1 != null){
				try {
					vWorkspace.getWorkspace().getCommandHistory().doCommand(comm1, vWorkspace);
				} catch (CommandException e) {
				}
			}
			columnPaths = vWorkspace.getRepFactory().getWorksheet(worksheetId).getHeaders().getAllPaths();
			// create edit multiple cells command
			for (HNodePath path : columnPaths) {
				if (path.getLeaf().getColumnName().compareTo(colnameString)==0) {
					selectedPath = path;
				}
			}
			JSONArray inputParamArr = this.createMultiCellCmd(rvco,selectedPath.getLeaf().getId());
			CommandFactory cf = ctrl.getCommandFactoryMap().get(MultipleValueEditColumnCommand.class.getSimpleName());
			JSONInputCommandFactory scf = (JSONInputCommandFactory)cf;
			Command comm = scf.createCommand(inputParamArr, vWorkspace);
			if(comm != null){
//				logger.info("Executing command: " + commObject.get(HistoryArguments.commandName.name()));
				vWorkspace.getWorkspace().getCommandHistory().doCommand(comm, vWorkspace);
			}
		}
		catch(Exception e)
		{
			System.out.println(""+e.toString());
		}
		UpdateContainer c = new UpdateContainer();
		Worksheet worksheet = vWorkspace.getWorkspace().getWorksheet(worksheetId);
		vWorkspace.getViewFactory().updateWorksheet(vWorksheetId, worksheet,worksheet.getHeaders().getAllPaths(), vWorkspace);
		vWorkspace.getViewFactory().getVWorksheet(this.vWorksheetId).update(c);
		c.add(new InfoUpdate("Submit Complete"));
		return c;
	}
	@Override
	public UpdateContainer undoIt(VWorkspace vWorkspace) {
		return null;
	}

}

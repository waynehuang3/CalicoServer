package calico.plugins;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import calico.*;
import calico.components.*;
import calico.controllers.*;
import calico.events.CalicoEventHandler;
import calico.plugins.events.*;

public abstract class AbstractCalicoPlugin implements CalicoPlugin
{
	public Logger logger = null;//Logger.getLogger(AbstractCalicoPlugin.class.getName());
	
	
	// Plugin Info
	public CalicoPluginInfo PluginInfo = new CalicoPluginInfo();
	
	// Constructor
	public AbstractCalicoPlugin()
	{
		this.logger = Logger.getLogger(this.getClass().getName());
	}
	
	
	
	
	
	
	
	// LOGGING ////////////////////////////////////////////////////
	public void debug(String message){logger.debug(message);}
	public void error(String message){logger.error(message);}
	public void trace(String message){logger.trace(message);}
	public void warn(String message){logger.warn(message);}
	public void info(String message){logger.info(message);}
	/// END LOGGING ////////////////////////////////////////////////////
	
	///// HOOKS ////////////////////////////////////////////////////
	public void onPluginEnd(){}
	public void onPluginStart(){}
	
	public boolean registerNetworkCommandEvents()
	{
		List<Integer> commands = getNetworkCommands();
		
		for (Integer comm : commands)
		{
			if (!CalicoEventHandler.getInstance().addEvent(comm.intValue()))
				return false;
		}
		return true;
		
	}
	
	public ArrayList<Integer> getNetworkCommands()
	{
		return getNetworkCommands(getNetworkCommandsClass());
	}
	
	public static ArrayList<Integer> getNetworkCommands(Class<?> rootClass)
	{
		ArrayList<Integer> ret = new ArrayList<Integer>();
		Field[] fields = rootClass.getFields();
		
		try
		{
			for (int i = 0; i < fields.length; i++)
			{
				if (fields[i].getType() == int.class)
				{
					fields[i].setAccessible(true);
					int value = fields[i].getInt(rootClass);
					ret.add(new Integer(value));
					System.out.println("Registering event for: " + fields[i].getName() + ", value: " + fields[i].getInt(rootClass));
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return ret;
	}
	
	public abstract Class<?> getNetworkCommandsClass();
	
	public void onException(Exception e){}
	///// END HOOKS ////////////////////////////////////////////////////
	
	
	
	//// INTERNALS ////////////////////////////////////////////////////
	protected final void RegisterPluginEvent(Class<?> eventClass){RegisterPluginEvent(eventClass.getSimpleName(), eventClass);}
	protected final void RegisterPluginEvent(String eventName, Class<?> eventClass)
	{
		CalicoPluginManager.registerEvent(eventName, eventClass);
	}
	
	
	
	// Sends an event
	protected final void FireEvent(CalicoEvent event){FirePluginEvent(event);}
	protected final void FirePluginEvent(CalicoEvent event)
	{
		CalicoPluginManager.receivePluginEvent(this.getClass(), event);
	}
	
	// Register an admin command
	protected final void RegisterAdminCommand(String command, String methodCallback)
	{
		try
		{
			Method method = this.getClass().getMethod(methodCallback, calico.plugins.PluginCommandParameters.class, java.lang.StringBuilder.class);
			
			CalicoPluginManager.registerAdminCommandCallback(command, method, this);
		}
		catch(NoSuchMethodException e)
		{
			error("Error Registering command \""+command+"\" - no such method \""+methodCallback+"(PluginCommandParameters, StringBuilder)\"");
		}
	}
	
	
	///// CONFIG LOADING
	protected final String GetConfigString(String key){return CalicoConfig.getconfig_String(key);}
	protected final int GetConfigInt(String key){return CalicoConfig.getconfig_int(key);}
	protected final float GetConfigFloat(String key){return CalicoConfig.getconfig_float(key);}
	protected final double GetConfigDouble(String key){return CalicoConfig.getconfig_double(key);}
	protected final long GetConfigLong(String key){return CalicoConfig.getconfig_long(key);}
	protected final boolean GetConfigBool(String key){return CalicoConfig.getconfig_boolean(key);}	
	
	
	
	///// COMPONENT LOADING
	protected final CGroup GetScrap(long uuid){return CGroup.getGroup(uuid);}
	protected final CArrow GetArrow(long uuid){return CArrowController.arrows.get(uuid);}
	protected final CStroke GetStroke(long uuid){return CStrokeController.strokes.get(uuid);}
	protected final CCanvas GetCanvas(long uuid){return CCanvasController.canvases.get(uuid);}
	
	
	/// END INTERNALS ////////////////////////////////////////////////////
	
	
}

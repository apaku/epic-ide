package org.epic.debug;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.help.browser.IBrowser;
import org.eclipse.help.internal.browser.BrowserDescriptor;
import org.eclipse.help.internal.browser.BrowserManager;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.epic.core.views.browser.BrowserView;
import org.epic.debug.cgi.CustomBrowser;
import org.epic.debug.util.CGIProxy;
import org.epic.debug.util.DebuggerProxy;
import org.epic.debug.util.PathMapper;
import org.epic.debug.util.PathMapperCygwin;
import org.epic.debug.util.PathMapping;
import org.epic.debug.util.RemotePort;
import org.epic.perleditor.PerlEditorPlugin;
import org.epic.perleditor.editors.util.PerlExecutableUtilities;
import org.epic.regexp.views.RegExpView;

/**
 * @author ruehl
 * 
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates. To enable and disable the creation of type
 * comments go to Window>Preferences>Java>Code Generation.
 */
public class RemoteTarget extends DebugTarget implements IDebugEventSetListener
{
		
	private boolean mDebug;

	private boolean mShutDownStarted;

	private boolean mReConnect;

	private RemoteTarget mTarget;
	
	private DebuggerProxy mProxy;

	private String mRemoteDest;

	private PathMapper mMapper;
	private String mProjectName;
	private String mIP;

	private String mStartupFileRelPath;

	private String mStartUpFileDirPath;

	private String mPort;

	private boolean mTerminated;
	
	/**
	 * Constructor for DebugTarget.
	 */
	public RemoteTarget()
	{
		super();
		mTerminated = false;
	}

	/**
	 * Constructor for DebugTarget.
	 */
	public RemoteTarget(ILaunch launch)
	{
		
		super(launch);
		mTerminated = false;
		mDebug = launch.getLaunchMode().equals(ILaunchManager.DEBUG_MODE);
		mProcessName = "Perl Debugger";
		
			mProcessName = "CGI Perl";
		DebugPlugin.getDefault().addDebugEventListener(this);
		try {
			mIP =
				mLaunch.getLaunchConfiguration().getAttribute(
					PerlLaunchConfigurationConstants.ATTR_REMOTE_HOST,
					EMPTY_STRING);
			mPort =
				mLaunch.getLaunchConfiguration().getAttribute(
					PerlLaunchConfigurationConstants.ATTR_REMOTE_PORT,
					EMPTY_STRING);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void start()
	{
		mReConnect = true;
		initPath();
		createPathMapper();
		RemotePackage.create(this);
		
		if (!startTarget())
			terminate();

		if (mDebug)
		{

			if (!startSession())
				terminate();
		}
	}

	/**
	 * 
	 */
	private void createPathMapper() {
		mMapper = new PathMapper();
		mMapper.add(new PathMapping(mProjectDir.toString(), mRemoteDest));
	}

	boolean startTarget()
	{

		if (mDebug)
		{
			mDebugPort = new RemotePort();
			mDebugPort.startConnect();
			if (mDebugPort == null)
				return false;
		}
						
		
		return true;

	}
	/**
	 * Fire a debug event marking the creation of this element.
	 */
	private void fireCreationEvent(Object fSource)
	{
		fireEvent(new DebugEvent(fSource, DebugEvent.CREATE));
	}

	/**
	 * Fire a debug event
	 */
	private void fireEvent(DebugEvent event)
	{
		DebugPlugin manager = DebugPlugin.getDefault();
		if (manager != null)
		{
			manager.fireDebugEventSet(new DebugEvent[] { event });
		}
	}

	
	boolean startSession()
	{
		/* start debugger */
		if (connectDebugger(false) != RemotePort.mWaitOK)
			return false;
		
		try {
			mProxy = new DebuggerProxy(mPerlDB,"Remote Process");
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		mLaunch.addProcess(mProxy);
		fireCreationEvent(mProxy);

		return true;
	}

	/**
	 * @see org.eclipse.debug.core.model.ITerminate#isTerminated()
	 */
	public boolean isTerminated()
	{
		if (mPerlDB == null)
			return (!mReConnect);

		return mPerlDB.isTerminated(this) && !mReConnect;
	}

	/**
	 * @see org.eclipse.debug.core.model.ITerminate#terminate()
	 */
	public void terminate()
	{
		mReConnect = false;
		shutdown();
	}

	Process startPerlProcess()
	{
		return null;
	}

	void debugSessionTerminated()
	{

		if (mDebugPort != null)
			mDebugPort.shutdown();

		//	 mTarget = new CGITarget(mLaunch);
		//	 mTarget.mProcessName ="New";

		mTarget = this;

		Thread term = new Thread()
		{
			public void run()
			{
				mTarget.startSession();
				mLaunch.addDebugTarget(mTarget);
				((DebugTarget) mTarget).getDebuger().generateDebugInitEvent();
				getDebuger().generateDebugInitEvent();
			}
		};

		term.start();

		mLaunch.removeDebugTarget(this);
		//	mTarget.fireCreateEvent();

		//	fireTerminateEvent();
		//	fireTerminateEvent();
		mReConnect = false;

		fireChangeEvent();

		//((DebugTarget) mTarget).getDebuger().generateDebugInitEvent();
		return;
	}

	// Copies src file to dst file.
	// If the dst file does not exist, it is created
	void copy(File src, File dst, String fAppend) throws IOException
	{
		InputStream in = new FileInputStream(src);
		OutputStream out = new FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
		{
			out.write(buf, 0, len);
		}
		out.write(fAppend.getBytes(), 0, fAppend.length());
		in.close();
		out.close();
	}

	void initPath()
	{

		String startfile = null;
		String prjName = null;
		String progParams = null;
		String dest = null;

		
		try
		{
			startfile =
				mLaunch.getLaunchConfiguration().getAttribute(
					PerlLaunchConfigurationConstants.ATTR_STARTUP_FILE,
					EMPTY_STRING);
			prjName =
				mLaunch.getLaunchConfiguration().getAttribute(
					PerlLaunchConfigurationConstants.ATTR_PROJECT_NAME,
					EMPTY_STRING);
			dest =
				mLaunch.getLaunchConfiguration().getAttribute(
					PerlLaunchConfigurationConstants.ATTR_REMOTE_DEST,
					EMPTY_STRING);

		} catch (Exception ce)
		{
			PerlDebugPlugin.log(ce);
		}
		
		mProjectName = prjName;
		
		IProject prj =
			PerlDebugPlugin.getWorkspace().getRoot().getProject(prjName);
		
		IPath path;
		path = new Path(startfile);
		mStartUpFileDirPath = path.removeLastSegments(1).toString();
		mStartupFileRelPath = startfile;
		mProjectDir = prj.getLocation();
		path = mProjectDir.append(startfile);
		mWorkingDir = path.removeLastSegments(1);
		mStartupFile = path.lastSegment();
		mStartupFileAbsolut = dest +"/"+startfile;
		mRemoteDest = dest;
		
	}

	
	public String getStartupFileRelPath()
	{
		return (mStartupFileRelPath);
	}
	
	public String getProjectName()
	{
		return (mProjectName);
	}
	
	public void shutdown(boolean unregister)
	{
		mTerminated = true;
		if (mShutDownStarted)
			return;
		mReConnect = false;
		mShutDownStarted = true;

		super.shutdown(unregister);
		try
		{
			mProxy.terminate();
		} catch (DebugException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		DebugPlugin.getDefault().removeDebugEventListener(this);
	}

	public void handleDebugEvents(DebugEvent[] events)
	{
		for (int i = 0; i < events.length; i++)
		{
			if (events[i].getKind() == DebugEvent.TERMINATE)
				if (events[i].getSource() == mProcess
					|| events[i].getSource() == mProxy)
					DebugPlugin.getDefault().asyncExec(new Runnable()
				{
					public void run()
					{
						terminate();
					}
				});
		}
	}

	public IProcess getProcess()
	{
		return mProxy;
	}

	/* (non-Javadoc)
	 * @see org.epic.debug.Target#isLocal()
	 */
	boolean isLocal() {
		
		return false;
	}
	
	public PathMapper getPathMapper()
	{
		return (mMapper);
	}
	/**
	 * @return Returns the mStartUpFileDirPath.
	 */
	public String getStartUpFileDirPath() {
		return mStartUpFileDirPath;
	}
	/**
	 * @return Returns the mIP.
	 */
	public String getIP() {
		return mIP;
	}
	/**
	 * @return Returns the mPort.
	 */
	public String getPort() {
		return mPort;
	}
	/**
	 * @return Returns the mRemoteDest.
	 */
	public String getRemoteDest() {
		return mRemoteDest;
	}
	
	public boolean canTerminate()
	{
		if( mPerlDB == null )
		{
			return( ! mTerminated);
		}
		return !isTerminated();
	}
}
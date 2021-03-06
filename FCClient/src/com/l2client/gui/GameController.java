package com.l2client.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.Light;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.CameraNode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.shadow.DirectionalLightShadowFilter;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.system.AppSettings;
import com.l2client.app.Singleton;
import com.l2client.controller.SceneManager.Action;
import com.l2client.dao.UserPropertiesDAO;
import com.l2client.gui.dialogs.CharCreateJPanel;
import com.l2client.gui.dialogs.ChatPanel;
import com.l2client.gui.dialogs.GameServerJPanel;
import com.l2client.gui.dialogs.TransparentLoginPanel;
import com.l2client.model.jme.NPCModel;
import com.l2client.model.jme.NewCharacterModel;
import com.l2client.model.jme.VisibleModel;
import com.l2client.model.network.ClientFacade;
import com.l2client.model.network.GameServerInfo;
import com.l2client.network.game.ClientPackets.CharacterCreate;
import com.l2client.network.login.LoginHandler;


/**
 * game controller for switching game states;
 * start screen
 * login
 * char selection/creation
 * server selection
 * in game
 */
//TODO check if jme gamestates could replace this
public final class GameController {

	private static final String SCENES_CREATE = "scenes/create/create.j3o";
	private static final String SCENES_SELECT = "scenes/select/select.j3o";

	private static final Logger logger = Logger.getLogger(GameController.class
            .getName());
	
	private static GameController singleton;
	
//	private boolean finished = false;
	
	private boolean worldEntered = false;
	
	private final NewCharacterModel charSummary = new NewCharacterModel(null);

	private ClientFacade clientInfo;

	private LoginHandler loginHandler;

//	private SceneRoot sceneRoot;

	private Camera camera;

	private AppSettings settings;

	/**
	 * viewport needed for post process filter integration
	 */
//	private ViewPort viewPort;
	
	private GameController(){	
	}
	
	public static GameController get(){
		if(singleton == null){
			synchronized (GameController.class) {
				if(singleton == null){
					singleton = new GameController();
				}
			}
		}
		return singleton;
	}	
	
	public void initialize(Camera cam, AppSettings settings /*, ViewPort viewPort*/){
//		sceneRoot = Singleton.get().getSceneManager().getRoot();
		Singleton.get().getSceneManager().removeAll();
		camera = cam;
//		this.viewPort = viewPort;
		this.settings = settings;
	}
	
	public void doEnterWorld(){
		if(worldEntered)
			return;
		
		worldEntered = true;
//		if(sceneRoot==null)
//			return;
//		//reset scene
//		sceneRoot.cleanupScene();
		Singleton.get().getSceneManager().removeAll();
//		TextureManager.doTextureCleanup();
		//reset GUI
		Singleton.get().getGuiController().removeAll();
		System.gc();
		//setup camera to be centered around player (char selecetd, or ingame object package?)
		//hook up game input controller
		Singleton.get().getCharController().onEnterWorld(clientInfo.getCharHandler(), camera);
//		//setup in game GUI
		setupGameGUI();
//		//startup of asset loading for area around char
//		sceneRoot.updateModelBound();
//		sceneRoot.updateGeometricState();
		
		//does not look good
//		FilterPostProcessor fpp = new FilterPostProcessor(Singleton.get().getAssetManager().getJmeAssetMan());
//		SSAOFilter ssaoFilter = new SSAOFilter(12.940201f, 43.928635f,
//				0.32999992f, 0.6059958f);
//		fpp.addFilter(ssaoFilter);
//		logger.severe("Adding SSAO");
//		Singleton.get().getSceneManager().changePostProcessor(fpp, Action.ADD);
	}
	
	private void setupGameGUI() {
		// Actions GUI (start loading somewhat earlier, but only here as in onEnterWorld
		//              the inGame inputHandler is created)
//		ActionManager.getInstance().loadActions();
		//Chat GUI
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				
				Singleton.get().getGuiController().displayAdminTelePanel();
				
				final ChatPanel pan = Singleton.get().getGuiController().displayChatJPanel();
				clientInfo.getChatHandler().setChatPanel(pan);
				pan.addChatListener(new KeyListener() {
					
					@Override
					public void keyTyped(KeyEvent e) {
					}
					
					@Override
					public void keyReleased(KeyEvent e) {			
					}
					
					@Override
					public void keyPressed(KeyEvent e) {
						if(KeyEvent.VK_ENTER == e.getKeyCode()){
							clientInfo.getChatHandler().sendMessage(pan.getChatMessage());
						}
					}
				});
			}
		});
	}

	/**
	 * Initialize the character selection based on the characters stored in the 
	 * {@link ClientFacade} CharSelectHandler
	 */
	public void doCharSelection(){
//		if(sceneRoot==null)
//			return;
//		//reset scene
//		sceneRoot.cleanupScene();
		Singleton.get().getSceneManager().removeAll();
		Singleton.get().getGuiController().removeAll();

		//does not look good
//		FilterPostProcessor fpp = new FilterPostProcessor(Singleton.get().getAssetManager().getJmeAssetMan());
//		SSAOFilter ssaoFilter = new SSAOFilter(12.940201f, 43.928635f,
//				0.32999992f, 0.6059958f);
//		fpp.addFilter(ssaoFilter);
//		Singleton.get().getSceneManager().changePostProcessor(fpp, Action.ADD);

		//display available chars + gui for creation of new one
		//if none present go directly for creation of new char
        if (clientInfo.getCharHandler().getCharCount() > 0) {
        	doCharPresentation();
		} else {
			doCharCreation();
		}
	}
	
	public void doCharCreation() {
		
		Singleton.get().getSceneManager().removeAll();
		
		try{
			Node n = (Node) Singleton.get().getAssetManager().getJmeAssetMan()
					.loadModel(SCENES_CREATE);
			Singleton.get().getSceneManager().changeTerrainNode(n, Action.ADD);
			
//			//this is needed as the ssao pass will other wise only render 
//			//the shadow of our attached chars as they are on a different 
//			//root (nice for a ghost effect or so)
//			for(Light l : n.getLocalLightList()){
//				if(l instanceof AmbientLight){
//					n.removeLight(l);
//					l.setColor(new ColorRGBA(0.6f,0.6f,0.8f,1.0f));
//					Singleton.get().getSceneManager().changeRootLight(l, Action.ADD);
//				}
//			}
			
			//ARGH !! the troll in the sdk is a standard jme anim troll,
			//it does not work without being loaded by the standard animation package using animationproviders
			//so relaping it here
			Spatial troll = n.getChild("troll");	
			if(troll != null){
				n.detachChild(troll);
				VisibleModel newtroll = new VisibleModel(null);
				newtroll.attachVisuals();
				newtroll.setLocalTranslation(troll.getLocalTranslation());
				n.attachChild(newtroll);
			}
			//this is needed as the chars are on a different node and would not be rendered
			//just add, do not remove here..
			for(Light l : n.getLocalLightList()){
					Singleton.get().getSceneManager().changeRootLight(l, Action.ADD);
			}
				
		} catch (Exception e1) {
			logger.log(Level.SEVERE, "Failed to load creation scene file "+SCENES_CREATE, e1);
		}
		
		camera.setLocation(new Vector3f(2.1353703f, 0.10786462f, 14.364603f));
		camera.lookAtDirection(new Vector3f(-0.1764535f, 0.27474004f, -0.94518876f), Vector3f.UNIT_Y);
		
		//FIXME move to own class
		//for the first just display the menu for char selection which steers the display
		//Name, Sex, Race, Class, (HairStyle, HairColor, Face) 
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				final CharCreateJPanel pan = Singleton.get().getGuiController()
						.displayCharCreateJPanel();
				
				// action that gets executed in the update thread:
				pan.addCreateActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
								clientInfo.sendGamePacket(new CharacterCreate(pan.getNewCharSummary()));
								//dialog will stay open, will be closed on
								//create ok package or cancel				
							}
						});
				pan.addCancelActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						// this gets executed in jme thread
						// do 3d system calls in jme thread only!
						doCharPresentation();
					}
				});
				pan.addModelchangedListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						// this gets executed in jme thread
						// do 3d system calls in jme thread only!

						Singleton.get().getSceneManager().removeChar();
						
						//FIXME reevaluate model composition
						charSummary.setNewCharSummary(pan.getNewCharSummary());
						charSummary.attachVisuals();
						charSummary.setLocalTranslation(.126f, -0.1224f, 7.76f);
						Singleton.get().getSceneManager().changeCharNode(charSummary,Action.ADD);
						//FIXME end of move this out
					}
				});
				pan.afterDisplayInit();
			}
		});
		//NICE TO HAVE:
		//display x characters for x races
		//change input handler to only allow left,right, escape and enter
		//add input handler for clicking on one of the chars for selection
		//add functionality to zoom around or fade away not used chars
		//display a choose current char window on down
		//if chosen display the char customization window and an accept/cancel
		
		//exit to charPresentation on accept of a char or a cancel (if charCount > 0)
		//purge root on exit
		
	}

	/**
	 * comparable with the display of the characters in the lobby a player has for entering the world
	 */
	private void doCharPresentation() {			
		//purge root
		Singleton.get().getSceneManager().removeAll();
		
//		//FIXME for testcase use first one, remove later
//		{
//			clientInfo.getCharHandler().setSelected(0);
//			clientInfo.getCharHandler().onCharSelected();
//			if(true)return;
//		}
		
		//TODO load the hall
		//load the x representations of the characters into the hall
		//add input handler for choosing a char and functionality to let him step to the front
		//add gui buttons for enter world, exit, options
		//on enter world start game with the chosen, on exit cleanup, on options show options pane

		Node scene = null;
		Vector3f pos = null;
		try{
			scene = (Node) Singleton.get().getAssetManager().getJmeAssetMan()
					.loadAsset(SCENES_SELECT);		
			if(scene != null){
				
				//this is needed as the chars are on a different node and would not be rendered
				//just add, do not remove here..
				for(Light l : scene.getLocalLightList()){
						Singleton.get().getSceneManager().changeRootLight(l, Action.ADD);
				}
		
				
				Singleton.get().getSceneManager().changeTerrainNode(scene, Action.ADD);
				
				//BEWARE !! do not use world pos at that time the node is not attached, and world pos was not computed
				pos = scene.getChild("target").getLocalTranslation().clone();
				camera.setLocation(pos); 
				//default, changed to last selected
				camera.lookAt(scene.getChild("pos1").getLocalTranslation(), Vector3f.UNIT_Y);
				int chars = clientInfo.getCharHandler().getCharCount();
				if(chars > 12){
					logger.log(Level.SEVERE, "More than 12 characters present for selection! Check server settings! We can only display 12 chars for selection");
					chars = 12;
				}
				
				for (int i = 0; i < chars; i++) {
					if(clientInfo.getCharHandler().getSelectedChar().isLastUsed()){
						clientInfo.getCharHandler().setSelected(i);
						camera.lookAt(scene.getChild("pos"+(i+1)).getLocalTranslation(), Vector3f.UNIT_Y);
						CameraNode cn;
						
					}
					NewCharacterModel v = new NewCharacterModel(clientInfo.getCharHandler().getCharSummary(i));
					v.attachVisuals();
					pos = scene.getChild("pos"+(i+1)).getLocalTranslation().clone();
					pos.y -= 1.0f;
					v.setLocalTranslation(pos);
					v.setLocalRotation(new Quaternion().fromAngleNormalAxis((float) Math.PI, Vector3f.UNIT_Y.negate()));
					Singleton.get().getSceneManager().changeCharNode(v,Action.ADD);
				}				
			}
		} catch (Exception e1) {
			logger.log(Level.SEVERE, "Failed to load select scene file "+SCENES_SELECT, e1);
		}
    	
		final Node refScene = scene;
		
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				final JButton left = Singleton.get().getGuiController().displayButton("<", 40, 30, (settings.getWidth()/2)-100, settings.getHeight()-50);
				final JButton b = Singleton.get().getGuiController().displayButton("select", 80, 30, (settings.getWidth()/2)-40, settings.getHeight()-50);
				final JButton bb = Singleton.get().getGuiController().displayButton("create", 80, 30, (settings.getWidth()/2)-40, settings.getHeight()-10);
				final JButton right = Singleton.get().getGuiController().displayButton(">", 40, 30, (settings.getWidth()/2)+70, settings.getHeight()-50);
				left.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						int sel = clientInfo.getCharHandler().getSelectedIndex();
						if(sel < 1)
							return;
						
						sel--;
						clientInfo.getCharHandler().setSelected(sel);
						camera.lookAt(refScene.getChild("pos"+(sel+1)).getLocalTranslation(), Vector3f.UNIT_Y);
					}
				});
				right.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						int sel = clientInfo.getCharHandler().getSelectedIndex();
						if(sel+1 >= clientInfo.getCharHandler().getCharCount())
							return;
						
						sel++;
						clientInfo.getCharHandler().setSelected(sel);
						camera.lookAt(refScene.getChild("pos"+(sel+1)).getLocalTranslation(), Vector3f.UNIT_Y);
					}
				});
				b.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						// FIXME choose char and not select first, remove
						clientInfo.getCharHandler().setSelected(0);
						clientInfo.getCharHandler().onCharSelected();
						//cleanup of the buttons
						Singleton.get().getGuiController().removeButton(new JButton[]{b,bb});
					}
				});
				
				bb.addActionListener(new ActionListener() {
					
					@Override
					public void actionPerformed(ActionEvent e) {
						doCharCreation();
					}
				});
				bb.setMultiClickThreshhold(1000L);
			}
		});
	}

	public void doLogin(){
		
////		FIXME for testcase use first one, remove later
//		if(!initNetwork("ghoust", new char[]{'g','h','o','u','s','t'}, "127.0.0.1:2106"))
//			throw new RuntimeException("Failed to init Network");
//		if(true) return;
		
		
	    camera.setLocation(new Vector3f(0,0,0));  
		
		Singleton.get().getSceneManager().removeAll();
	    Quad b = new Quad(80f,60f);
	    b.updateBound();
	    Geometry geom = new Geometry("backdrop", b);
	    Material mat = new Material(Singleton.get().getAssetManager().getJmeAssetMan(), "Common/MatDefs/Misc/Unshaded.j3md");
	    mat.setTexture("ColorMap", Singleton.get().getAssetManager().getJmeAssetMan().loadTexture("start/backdrop.png"));
	    geom.setMaterial(mat);
	    geom.setLocalTranslation(-40f, -30f, -90f);	    
	    Singleton.get().getSceneManager().changeTerrainNode(geom,Action.ADD);
	    
	    Quad b2 = new Quad(38f,29f);
	    b2.updateBound();
	    Geometry geom2 = new Geometry("wolf", b2);
	    Material mat2 = new Material(Singleton.get().getAssetManager().getJmeAssetMan(), "Common/MatDefs/Misc/Unshaded.j3md");
	    mat2.setTexture("ColorMap", Singleton.get().getAssetManager().getJmeAssetMan().loadTexture("start/wolf.png"));
	    mat2.getAdditionalRenderState().setBlendMode(BlendMode.Alpha); // activate transparency
	    geom2.setMaterial(mat2);
	    geom2.setQueueBucket(Bucket.Transparent);
	    geom2.setLocalTranslation(-39f, -15f, -90f);	
	    Singleton.get().getSceneManager().changeTerrainNode(geom2,Action.ADD);
	    
		AmbientLight al = new AmbientLight();
	    al.setColor(new ColorRGBA(.8f, .8f, .8f, 1.0f));
		Singleton.get().getSceneManager().changeRootLight(al,Action.ADD);
		//#############################################################

		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				final TransparentLoginPanel pan = Singleton.get().getGuiController()
						.displayUserPasswordJPanel();
				// get properties initialized from file or by defaut 
				pan.setServer(System.getProperty(UserPropertiesDAO.SERVER_HOST_PROPERTY)+":"+System.getProperty(UserPropertiesDAO.SERVER_PORT_PROPERTY));
				// action that gets executed in the update thread:
				pan.addLoginActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent e) {
								// this gets executed in jme thread
								// do 3d system calls in jme thread only!
//								SoundController.getInstance().playOnetime("sound/click.ogg", false, Vector3f.ZERO);

						String[] split = pan.getServer().split(":");

						if(split.length<2) {
							Singleton.get().getGuiController().showErrorDialog("Check your server:port entry");
							return;
						}
						try {
							//store setting in user property for later usage
							System.setProperty(UserPropertiesDAO.SERVER_HOST_PROPERTY,split[0]);
							System.setProperty(UserPropertiesDAO.SERVER_PORT_PROPERTY,split[1]);
							//intentionally not used
							Integer.parseInt(split[1]);
						} catch (NumberFormatException ex) {
							Singleton.get().getGuiController().showErrorDialog("Your port is not a number entry");
							return;
						}
								if ( !initNetwork(pan.getUsername(), pan.getPassword(), pan.getServer()) ) {

									doLogin();
									Singleton.get().getGuiController()
											.showErrorDialog(
													"Failed to Connect to login server");

								} else {
									//save port and host to user.home on a successfull login
									UserPropertiesDAO.saveProperties();
									Singleton.get().getGuiController().removeAll();
								}
							}
						});
				pan.addCancelActionListener(new ActionListener(){
					@Override
					public void actionPerformed(ActionEvent e) {
						// this gets executed in jme thread
						// do 3d system calls in jme thread only!
//						finished = true;
////						SoundController.getInstance().playOnetime("sound/click.ogg", false, Vector3f.ZERO);
//						try {
//							Thread.sleep(1500);
//						} catch (InterruptedException ex) {
//						}
					}
				});
			}
		});
	}
	
	public boolean initNetwork(String user, char [] pwd, String hostport){

		String[] split = hostport.split(":");
		//verified by GUI already
		Integer port = Integer.parseInt(split[1]);
		
		this.clientInfo = Singleton.get().getClientFacade();
		
		clientInfo.init(user);
		
		//try connection to login server
        this.loginHandler = new LoginHandler(port, split[0]){
            @Override
            public void onDisconnect(boolean todoOk,String host, int port){
                if(todoOk){
                 	clientInfo.connectToGameServer(host, port, loginOK1, loginOK2, playOK1, playOK2);
                }
            }
            @Override
            public void onServerListReceived(GameServerInfo[] servers){
            	
//            	FIXME for testcase use first one, remove later
            	requestServerLogin(0);
            	if(true) return;
            	
            	
            	//game server selection
            	if(servers != null && servers.length >0){
            		final GameServerJPanel p = Singleton.get().getGuiController().displayServerSelectionJPanel(servers);
            		p.addCancelActionListener(new ActionListener(){
    					@Override
    					public void actionPerformed(ActionEvent e) {
    						// this gets executed in jme thread
    						// do 3d system calls in jme thread only!
    						doDisconnect(false, "", -1);
    						//FIXME this is just for the testcase
    						doLogin();
    					}
    				});
            		p.addSelectActionListener(new ActionListener(){
    					@Override
    					public void actionPerformed(ActionEvent e) {
    						// this gets executed in jme thread
    						// do 3d system calls in jme thread only!
    						requestServerLogin(p.getSelectedServer());
    					}
    				});
            	}
            	else {
            		Singleton.get().getGuiController().showErrorDialog("Failed to Connect to login server");
            		logger.severe("Loginserver returned no gameservers to login to");
            		doDisconnect(false, "", -1);
            	}
            }
        };
        if(!loginHandler.connected)
        	return false;
        
        loginHandler.setLoginInfo(user,pwd);
        return true;
	}
	
	public void finish(){
//		finished = true;
		if(clientInfo!= null){
			clientInfo.cleanup();
			logger.info("Released Network Connections");
		}
	}
}

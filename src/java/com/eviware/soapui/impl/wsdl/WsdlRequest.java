/*
 *  soapUI, copyright (C) 2004-2008 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.config.CredentialsConfig;
import com.eviware.soapui.config.WsdlRequestConfig;
import com.eviware.soapui.impl.support.AbstractHttpRequest;
import com.eviware.soapui.impl.wsdl.submit.RequestTransportRegistry;
import com.eviware.soapui.impl.wsdl.submit.transports.http.AttachmentUtils;
import com.eviware.soapui.impl.wsdl.submit.transports.http.WsdlResponse;
import com.eviware.soapui.impl.wsdl.support.wsa.WsaConfig;
import com.eviware.soapui.model.ModelItem;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.iface.MessagePart;
import com.eviware.soapui.model.iface.SubmitContext;
import com.eviware.soapui.model.iface.Attachment.AttachmentEncoding;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansion;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionContainer;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionUtils;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionsResult;
import com.eviware.soapui.model.support.InterfaceListenerAdapter;
import com.eviware.soapui.settings.WsdlSettings;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.types.StringToStringMap;

/**
 * Request implementation holding a SOAP request
 * 
 * @author Ole.Matzura
 */

public class WsdlRequest extends AbstractHttpRequest<WsdlRequestConfig> implements WsdlAttachmentContainer, PropertyExpansionContainer
{
	public final static Logger log = Logger.getLogger( WsdlRequest.class );
	
	public static final String RESPONSE_CONTENT_PROPERTY = WsdlRequest.class.getName() + "@response-content";
	public static final String INLINE_RESPONSE_ATTACHMENTS = WsdlRequest.class.getName() + "@inline-response-attachments";
	public static final String EXPAND_MTOM_RESPONSE_ATTACHMENTS = WsdlRequest.class.getName() + "@expand-mtom-attachments";
	public static final String FORCE_MTOM = WsdlRequest.class.getName() + "@force_mtom";
	public static final String ENABLE_INLINE_FILES = WsdlRequest.class.getName() + "@enable_inline_files";
	public static final String SKIP_SOAP_ACTION = WsdlRequest.class.getName() + "@skip_soap_action";
	public static final String ENCODE_ATTACHMENTS = WsdlRequest.class.getName() + "@encode_attachments";
	public static final String WSS_TIMETOLIVE = WsdlRequest.class.getName() + "@wss-time-to-live";
	public static final String OPERATION_PROPERTY = WsdlRequest.class.getName() + "@operation";
	public static final String INCOMING_WSS = WsdlRequest.class.getName() + "@incoming-wss";
	public static final String OUGOING_WSS = WsdlRequest.class.getName() + "@outgoing-wss";
	
	public final static String PW_TYPE_NONE="None";
   public final static String PW_TYPE_DIGEST="PasswordDigest";
   public final static String PW_TYPE_TEXT="PasswordText";
   
   private WsdlOperation operation;
//   private WsdlResponse response;
   
	private List<HttpAttachmentPart> definedAttachmentParts;
	private InternalInterfaceListener interfaceListener = new InternalInterfaceListener();

	private WsaConfig wsaConfig;

   public WsdlRequest( WsdlOperation operation, WsdlRequestConfig callConfig )
   {
   	this( operation, callConfig, false );
   }
	
   public WsdlRequest( WsdlOperation operation, WsdlRequestConfig callConfig, boolean forLoadTest )
   {
   	super( callConfig, operation, null, forLoadTest );
   	
      this.operation = operation;
     
      initEndpoints();
      
      // ensure encoding
      if( callConfig.getEncoding() == null || callConfig.getEncoding().length() == 0 )
      {
      	callConfig.setEncoding( "UTF-8" );
      }

      if( !forLoadTest )
      {
	      operation.getInterface().addPropertyChangeListener( interfaceListener );
	      operation.getInterface().addInterfaceListener( interfaceListener );
      }
   }
   
   
	public void updateConfig(WsdlRequestConfig request)
	{
		setConfig( request );
	}
   
   protected void initEndpoints()
   {
   	if( getEndpoint() == null )
   	{
	      String[] endpoints = operation.getInterface().getEndpoints();
	      if( endpoints.length > 0 )
	      {
	         setEndpoint( endpoints[0] );
	      }
   	}
   }

   public boolean isInlineResponseAttachments()
   {
   	return getSettings().getBoolean( INLINE_RESPONSE_ATTACHMENTS );
   }
   
   public void setInlineResponseAttachments( boolean inlineResponseAttachments )
   {
   	boolean old = getSettings().getBoolean( INLINE_RESPONSE_ATTACHMENTS );
   	getSettings().setBoolean( INLINE_RESPONSE_ATTACHMENTS, inlineResponseAttachments );
   	notifyPropertyChanged( INLINE_RESPONSE_ATTACHMENTS, old, inlineResponseAttachments );
   }

   public boolean isExpandMtomResponseAttachments()
   {
   	return getSettings().getBoolean( EXPAND_MTOM_RESPONSE_ATTACHMENTS );
   }
   
   public void setExpandMtomResponseAttachments( boolean expandMtomResponseAttachments )
   {
   	boolean old = getSettings().getBoolean( EXPAND_MTOM_RESPONSE_ATTACHMENTS );
   	getSettings().setBoolean( EXPAND_MTOM_RESPONSE_ATTACHMENTS, expandMtomResponseAttachments );
   	notifyPropertyChanged( EXPAND_MTOM_RESPONSE_ATTACHMENTS, old, expandMtomResponseAttachments );
   }
   
   /**
    * Use getResponse().getContentAsString();
    * @deprecated
    */
   
   @Deprecated
   public String getResponseContent()
   {
      return getResponse() == null ? null : getResponse().getContentAsString();
   }

   public WsdlResponse getResponse()
   {
   	return (WsdlResponse) super.getResponse();
   }
   
   public WsdlOperation getOperation()
   {
      return operation;
   }
   
   public void setOperation( WsdlOperation wsdlOperation )
	{
   	WsdlOperation oldOperation = operation;
		this.operation = wsdlOperation;
		
		definedAttachmentParts = null;
		notifyPropertyChanged( OPERATION_PROPERTY, oldOperation, operation );
	}

   public void setRequestContent(String request)
   {
      definedAttachmentParts = null;
      super.setRequestContent(request);
   }
   
//   public void setResponse( WsdlResponse response, SubmitContext context )
//   {
//   	WsdlResponse oldResponse = getResponse();
//		this.response = response;
//		
//      notifyPropertyChanged( RESPONSE_PROPERTY, oldResponse, response );
//   }

	public WsdlSubmit<WsdlRequest> submit( SubmitContext submitContext, boolean async ) throws SubmitException
	{
      String endpoint = PropertyExpansionUtils.expandProperties( submitContext, getEndpoint());
		if( endpoint == null || endpoint.trim().length() == 0 )
      {
      	UISupport.showErrorMessage( "Missing endpoint for request [" + getName() + "]" );
      	return null;
      }
		
		try
		{
			WsdlSubmit<WsdlRequest> submitter = new WsdlSubmit<WsdlRequest>(this, getSubmitListeners(), 
					RequestTransportRegistry.getTransport(endpoint, submitContext));
			submitter.submitRequest(submitContext, async);
			return submitter;
		}
		catch( Exception e )
		{
			throw new SubmitException( e.toString() );
		}
	}
	
	private class InternalInterfaceListener extends InterfaceListenerAdapter implements PropertyChangeListener
	{
		public void propertyChange(PropertyChangeEvent evt)
		{
			if( evt.getPropertyName().equals( Interface.ENDPOINT_PROPERTY ))
			{
				String endpoint = getEndpoint();
				if( evt.getOldValue() != null && evt.getOldValue().equals( endpoint ))
				{
					setEndpoint( (String) evt.getNewValue() );
				}
			}
		}
	}

	public String getWssPasswordType()
	{
		String wssPasswordType = getConfig().getWssPasswordType();
		return StringUtils.isNullOrEmpty( wssPasswordType ) || PW_TYPE_NONE.equals( wssPasswordType ) ? null : wssPasswordType;
	}

	public void setWssPasswordType(String wssPasswordType)
	{
		if( wssPasswordType == null || wssPasswordType.equals( PW_TYPE_NONE ))
		{
			if( getConfig().isSetWssPasswordType() )
				getConfig().unsetWssPasswordType();
		}
		else
		{
			getConfig().setWssPasswordType( wssPasswordType );
		}
	}

	
	
	/* (non-Javadoc)
	 * @see com.eviware.soapui.impl.wsdl.AttachmentContainer#getDefinedAttachmentParts()
	 */
	public HttpAttachmentPart [] getDefinedAttachmentParts()
	{
		if( definedAttachmentParts == null )
		{
			try
			{
				UISupport.setHourglassCursor();
				definedAttachmentParts = AttachmentUtils.extractAttachmentParts( 
							operation, getRequestContent(), true, false, isMtomEnabled() );
			}
			catch (Exception e)
			{
				log.warn( e.toString() );
				definedAttachmentParts = new ArrayList<HttpAttachmentPart>();
			}
			finally 
			{
				UISupport.resetCursor();
			}
		}
		
		return definedAttachmentParts.toArray( new HttpAttachmentPart[definedAttachmentParts.size()] );
	}
	
	/* (non-Javadoc)
	 * @see com.eviware.soapui.impl.wsdl.AttachmentContainer#getAttachmentPart(java.lang.String)
	 */
	public HttpAttachmentPart getAttachmentPart( String partName )
	{
		HttpAttachmentPart[] parts = getDefinedAttachmentParts();
		for( HttpAttachmentPart part : parts )
		{
			if( part.getName().equals( partName ))
				return part;
		}
		
		return null;
	}



	public void copyTo(WsdlRequest newRequest, boolean copyAttachments, boolean copyHeaders)
	{
      newRequest.setEncoding( getEncoding() );
      newRequest.setEndpoint( getEndpoint() );
      newRequest.setRequestContent( getRequestContent() );
      newRequest.setWssPasswordType( getWssPasswordType() );
      
      CredentialsConfig credentials = getConfig().getCredentials();
      if( credentials != null)
      	newRequest.getConfig().setCredentials( (CredentialsConfig) credentials.copy() );

      if( copyAttachments )
      	copyAttachmentsTo( newRequest );
      
      if( copyHeaders )
      	newRequest.setRequestHeaders( getRequestHeaders() );
      
//      ((DefaultWssContainer)newRequest.getWssContainer()).updateConfig( ( WSSConfigConfig ) getConfig().getWssConfig().copy() );
	}
	
	/* (non-Javadoc)
	 * @see com.eviware.soapui.impl.wsdl.AttachmentContainer#isMtomEnabled()
	 */
	public boolean isMtomEnabled()
	{
		return getSettings().getBoolean( WsdlSettings.ENABLE_MTOM );
	}
	
	public void setMtomEnabled( boolean mtomEnabled )
	{
		getSettings().setBoolean( WsdlSettings.ENABLE_MTOM, mtomEnabled );
		definedAttachmentParts = null;
	}
	
	public boolean isInlineFilesEnabled()
	{
		return getSettings().getBoolean( WsdlRequest.ENABLE_INLINE_FILES );
	}
	
	public void setInlineFilesEnabled( boolean inlineFilesEnabled )
	{
		getSettings().setBoolean( WsdlRequest.ENABLE_INLINE_FILES, inlineFilesEnabled );
	}
	
	public boolean isSkipSoapAction()
	{
		return getSettings().getBoolean( WsdlRequest.SKIP_SOAP_ACTION);
	}
	
	public void setSkipSoapAction( boolean skipSoapAction )
	{
		getSettings().setBoolean( WsdlRequest.SKIP_SOAP_ACTION, skipSoapAction );
	}

	@Override
   public void release()
	{
		super.release();
		
		getOperation().getInterface().removeInterfaceListener( interfaceListener );
		getOperation().getInterface().removePropertyChangeListener( interfaceListener );
	}

	public MessagePart[] getRequestParts()
	{
		try
		{
			List<MessagePart> result = new ArrayList<MessagePart>();
			result.addAll( Arrays.asList( getOperation().getDefaultRequestParts() ));
			result.addAll( Arrays.asList( getDefinedAttachmentParts()) );
			
			return result.toArray( new MessagePart[result.size()] );
		}
		catch (Exception e)
		{
			SoapUI.logError( e );
			return new MessagePart [0];
		}		
	}
	
	public MessagePart[] getResponseParts()
	{
		try
		{
			List<MessagePart> result = new ArrayList<MessagePart>();
			result.addAll( Arrays.asList( getOperation().getDefaultResponseParts() ));
			
			if( getResponse() != null )
				result.addAll( AttachmentUtils.extractAttachmentParts( 
						getOperation(), getResponse().getContentAsString(), true, true, isMtomEnabled() ));
			
			return result.toArray( new MessagePart[result.size()] );
		}
		catch (Exception e)
		{
			SoapUI.logError( e );
			return new MessagePart [0];
		}		
	}
	
	public String getWssTimeToLive()
	{
		return getSettings().getString( WSS_TIMETOLIVE, null );
	}
	
	public void setWssTimeToLive( String ttl )
	{
		getSettings().setString( WSS_TIMETOLIVE, ttl );
	}

	public long getContentLength()
	{
		return getRequestContent().length();
	}

	public boolean isForceMtom()
	{
		return getSettings().getBoolean( FORCE_MTOM );
	}
	
	public void setForceMtom( boolean forceMtom )
   {
   	boolean old = getSettings().getBoolean( FORCE_MTOM );
   	getSettings().setBoolean( FORCE_MTOM, forceMtom );
   	notifyPropertyChanged( FORCE_MTOM, old, forceMtom );
   }
	
	public boolean isEncodeAttachments()
	{
		return getSettings().getBoolean( ENCODE_ATTACHMENTS );
	}
	
	public void setEncodeAttachments( boolean encodeAttachments )
   {
   	boolean old = getSettings().getBoolean( ENCODE_ATTACHMENTS );
   	getSettings().setBoolean( ENCODE_ATTACHMENTS, encodeAttachments );
   	notifyPropertyChanged( ENCODE_ATTACHMENTS, old, encodeAttachments );
   }
	
	public String getIncomingWss()
	{
		return getConfig().getIncomingWss();
	}
	
	public void setIncomingWss( String incomingWss )
   {
   	String old = getIncomingWss();
   	getConfig().setIncomingWss( incomingWss );
   	notifyPropertyChanged( INCOMING_WSS, old, incomingWss );
   }
	
	public String getOutgoingWss()
	{
		return getConfig().getOutgoingWss();
	}
	
	public void setOutgoingWss( String outgoingWss )
   {
   	String old = getOutgoingWss();
   	getConfig().setOutgoingWss( outgoingWss );
   	notifyPropertyChanged( OUGOING_WSS, old, outgoingWss );
   }
	
	public boolean isWsAddressing()
	{
		return getConfig().getUseWsAddressing();
	}
	
	public void setWsAddressing( boolean wsAddressing )
   {
   	boolean old = getConfig().getUseWsAddressing();
   	getConfig().setUseWsAddressing(wsAddressing);
   	notifyPropertyChanged( "wsAddressing", old, wsAddressing );
   }
	

   
   @SuppressWarnings("unchecked")
   public List<? extends ModelItem> getChildren()
   {
      return Collections.EMPTY_LIST;
   }
   
   public PropertyExpansion[] getPropertyExpansions()
	{
		PropertyExpansionsResult result = new PropertyExpansionsResult( this, this );
		result.addAll(super.getPropertyExpansions());
		
		StringToStringMap requestHeaders = getRequestHeaders();
		for( String key : requestHeaders.keySet())
		{
			result.addAll( PropertyExpansionUtils.extractPropertyExpansions( this, 
					new RequestHeaderHolder( requestHeaders, key ), "value" ));
		}
		
		return result.toArray();
	}
   
   public class RequestHeaderHolder
	{
		private final StringToStringMap valueMap;
		private final String key;

		public RequestHeaderHolder( StringToStringMap valueMap, String key )
		{
			this.valueMap = valueMap;
			this.key = key;
		}

		public String getValue()
		{
			return valueMap.get( key );
		}

		public void setValue( String value )
		{
			valueMap.put( key, value );
			setRequestHeaders( valueMap );
		}
	}

	public AttachmentEncoding getAttachmentEncoding(String partName)
	{
		return AttachmentUtils.getAttachmentEncoding( getOperation(), partName, false );
	}
	public WsaConfig getWsaConfig() {
		if (wsaConfig == null)
		{
			if (!getConfig().isSetWsaConfig())
			{
				getConfig().addNewWsaConfig();
			}
			wsaConfig = new WsaConfig(getConfig().getWsaConfig());
		}
		return wsaConfig;
	}

	public ModelItem getModelItem()
	{
		return this;
	}
}

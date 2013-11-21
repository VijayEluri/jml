package org.realityforge.jml;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.regex.Pattern;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

/**
 * Abstract class used to verify a Message matches a format.
 * Instances of this class should be stateless and thread-safe.
 */
public abstract class MessageVerifier
{
  /**
   * Verify the message matches a specific format.
   *
   * @throws Exception if message format fails to verify.
   */
  public abstract void verifyMessage( Message message ) throws Exception;

  /**
   * Cast message to specified type, raising an exception if not possible.
   */
  protected final <T> T castToType( final Message message, final Class<T> type )
    throws Exception
  {
    return MessageUtil.castToType( message, type );
  }

  /**
   * Return an exception for message, with specified problem and exception
   */
  protected final Exception exceptionFor( final Message message, final String problem, final Exception e )
    throws Exception
  {
    return MessageUtil.exceptionFor( message, problem, e );
  }

  /**
   * Create a verifier that expects expects a TextMessage with content
   * matching XSD specified at URL.
   */
  public static MessageVerifier newXSDVerifier( final URL url )
    throws Exception
  {
    return newXmlVerifier( "XSD", XMLConstants.W3C_XML_SCHEMA_NS_URI, url );
  }

  /**
   * Create a verifier that expects expects a TextMessage with content
   * matching schema specified at URL. The schema language must be supported
   * the underling java.xml.validation API. The string that specifies the
   * schema is typically one of the name spaces specified in {@link javax.xml.XMLConstants}.
   */
  public static MessageVerifier newSchemaBasedVerifier( final String schemaLanguage,
                                                        final URL url )
    throws Exception
  {
    return newXmlVerifier( "Schema", schemaLanguage, url );
  }

  /**
   * Create a MessageVerifier that expects a TextMessage with content
   * matching specified Pattern.
   */
  public static MessageVerifier newRegexVerifier( final Pattern pattern )
    throws Exception
  {
    return new RegexMessageVerifier( pattern );
  }

  private static MessageVerifier newXmlVerifier( final String schemaLabel,
                                                 final String schemaLanguage,
                                                 final URL url )
    throws Exception
  {
    if( null == schemaLanguage ) throw new NullPointerException( "schemaLanguage" );
    if( null == url ) throw new NullPointerException( "url" );
    final SchemaFactory factory = SchemaFactory.newInstance( schemaLanguage );
    final Schema schema = factory.newSchema( url );
    return new XmlMessageVerifier( schemaLabel + " loaded from " + url, schema.newValidator() );
  }

  private static class XmlMessageVerifier
    extends MessageVerifier
  {
    private final String _noMatchMessage;
    private final Validator _validator;

    private XmlMessageVerifier( final String noMatchMessage, final Validator validator )
    {
      _noMatchMessage = noMatchMessage;
      _validator = validator;
    }

    public void verifyMessage( final Message message ) throws Exception
    {
      final TextMessage textMessage = castToType( message, TextMessage.class );
      try
      {
        _validator.validate( new StreamSource( new ByteArrayInputStream( textMessage.getText().getBytes() ) ) );
      }
      catch( final Exception e )
      {
        throw exceptionFor( message, "failed to match " + _noMatchMessage + ".", e );
      }
    }
  }

  private static class RegexMessageVerifier
    extends MessageVerifier
  {
    private final Pattern _pattern;

    private RegexMessageVerifier( final Pattern pattern )
    {
      _pattern = pattern;
    }

    public void verifyMessage( final Message message ) throws Exception
    {
      final TextMessage textMessage = castToType( message, TextMessage.class );
      if( !_pattern.matcher( textMessage.getText() ).matches() )
      {
        throw exceptionFor( message, "failed to match pattern \"" + _pattern.pattern() + "\".", null );
      }
    }
  }
}


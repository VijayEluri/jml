package jml;

import javax.jms.Message;

class TestMessageVerifier
  extends MessageVerifier
{
  private final int m_limit;

  TestMessageVerifier( final int limit )
  {
    m_limit = limit;
  }

  @Override
  public void verifyMessage( final Message message ) throws Exception
  {
    final int value = message.getIntProperty( TestHelper.HEADER_KEY );
    if( value > m_limit )
    {
      throw new Exception( MessageUtil.errorMessageFor( message ) + " has header value = " +
                           value + " that exceeds " + m_limit );
    }
  }
}

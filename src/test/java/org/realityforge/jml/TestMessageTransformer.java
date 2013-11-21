package org.realityforge.jml;

import javax.jms.Message;
import javax.jms.Session;

class TestMessageTransformer
  extends MessageTransformer
{
  private final boolean _fail;
  private long _lastMessageTime;

  TestMessageTransformer( final boolean fail )
  {
    _fail = fail;
  }

  long getLastMessageTime()
  {
    return _lastMessageTime;
  }

  @Override
  public Message transformMessage( final Session session, final Message message ) throws Exception
  {
    _lastMessageTime = System.nanoTime();
    if( _fail ) throw new Exception();
    return message;
  }
}

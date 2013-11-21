package jml;

import org.testng.annotations.*;
import javax.jms.Connection;
import javax.jms.Session;
import java.util.LinkedList;

public class AbstractBrokerBasedTestCase
{
  private Connection _connection;
  private final LinkedList<Session> _sessions = new LinkedList<Session>();

  @BeforeSuite
  public void startupBroker()
    throws Exception
  {
    TestHelper.startupBroker();
  }

  @AfterSuite
  public void shutdownBroker()
    throws Exception
  {
    TestHelper.shutdownBroker();
  }

  @BeforeMethod
  public void initConnection()
    throws Exception
  {
    _connection = TestHelper.createConnection();
    _connection.start();
  }

  @AfterMethod
  public void shutdownConnection()
    throws Exception
  {
    for( final Session session : _sessions )
    {
      session.close();
    }
    _sessions.clear();
    if( null != _connection )
    {
      _connection.stop();
      _connection.close();
      _connection = null;
    }
  }

  final Session createSession()
    throws Exception
  {
    return createSession( false, Session.AUTO_ACKNOWLEDGE );
  }

  final Session createSession( final boolean transacted, final int acknowledgeMode )
    throws Exception
  {
    final Session session = getConnection().createSession( transacted, acknowledgeMode );
    _sessions.add( session );
    return session;
  }

  final Connection getConnection()
  {
    if( null == _connection )
    {
      throw new IllegalStateException( "null == _connection" );
    }
    return _connection;
  }
}
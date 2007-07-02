package com.bretth.osmosis.mysql.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.bretth.osmosis.OsmosisRuntimeException;


/**
 * This class manages the lifecycle of JDBC objects to minimise the risk of
 * connection leaks and to support a consistent approach to database access.
 * 
 * @author Brett Henderson
 */
public class DatabaseContext {
	private static boolean driverLoaded;
	
	private String host;
	private String database;
	private String user;
	private String password;
	private Connection connection;
	
	
	/**
	 * Creates a new instance.
	 * 
	 * @param host
	 *            The server hosting the database.
	 * @param database
	 *            The database instance.
	 * @param user
	 *            The user name for authentication.
	 * @param password
	 *            The password for authentication.
	 */
	public DatabaseContext(String host, String database, String user, String password) {
		this.host = host;
		this.database = database;
		this.user = user;
		this.password = password;
	}
	
	
	/**
	 * Utility method for ensuring that the database driver is registered.
	 */
	private static void loadDatabaseDriver() {
		if (!driverLoaded) {
			// Lock to ensure two threads don't try to load the driver at the same time.
			synchronized (DatabaseContext.class) {
				// Check again to ensure another thread hasn't loaded the driver
				// while we waited for the lock.
				if (!driverLoaded) {
					try {
						Class.forName("com.mysql.jdbc.Driver");
						
					} catch (ClassNotFoundException e) {
						throw new OsmosisRuntimeException("Unable to find database driver.", e);
					}
					
					driverLoaded = true;
				}
			}
		}
	}
	
	
	/**
	 * If no database connection is open, a new connection is opened. The
	 * database connection is then returned.
	 * 
	 * @return The database connection.
	 */
	private Connection getConnection() {
		if (connection == null) {
			
			loadDatabaseDriver();
			
			try {
				connection = DriverManager.getConnection(
					"jdbc:mysql://" + host + "/" + database + "?"
			    	+ "user=" + user + "&password=" + password
			    );
				
			} catch (SQLException e) {
				throw new OsmosisRuntimeException("Unable to establish a database connection.", e);
			}
		}
		
		return connection;
	}
	
	
	/**
	 * Creates a new database prepared statement.
	 * 
	 * @param sql
	 *            The statement to be created.
	 * @return The newly created statement.
	 */
	public PreparedStatement prepareStatement(String sql) {
		try {
			PreparedStatement preparedStatement;
			
			preparedStatement = getConnection().prepareStatement(sql);
			
			return preparedStatement;
			
		} catch (SQLException e) {
			throw new OsmosisRuntimeException("Unable to create database prepared statement.", e);
		}
	}
	
	
	/**
	 * Creates a new database statement that is configured so that any result
	 * sets created using it will stream data from the database instead of
	 * returning all records at once and storing in memory.
	 * <p>
	 * If no input parameters need to be set on the statement, use the
	 * executeStreamingQuery method instead.
	 * 
	 * @param sql
	 *            The statement to be created. This must be a select statement.
	 * @return The newly created statement.
	 */
	public PreparedStatement prepareStatementForStreaming(String sql) {
		try {
			PreparedStatement statement;
			
			// Create a statement for returning streaming results.
			statement = getConnection().prepareStatement(
					sql,
					ResultSet.TYPE_FORWARD_ONLY,
					ResultSet.CONCUR_READ_ONLY);
			
			statement.setFetchSize(Integer.MIN_VALUE);
			
			return statement;
			
		} catch (SQLException e) {
			throw new OsmosisRuntimeException("Unable to create streaming resultset statement.", e);
		}
	}
	
	
	/**
	 * Creates a result set that is configured to stream results from the
	 * database.
	 * 
	 * @param sql
	 *            The query to invoke.
	 * @return The result set.
	 */
	public ResultSet executeStreamingQuery(String sql) {
		try {
			PreparedStatement statement;
			ResultSet resultSet;
			
			statement = prepareStatementForStreaming(sql);
			resultSet = statement.executeQuery();
			
			statement.close();
			
			return resultSet;
			
		} catch (SQLException e) {
			throw new OsmosisRuntimeException("Unable to create streaming resultset.", e);
		}
	}
	
	
	/**
	 * Commits any outstanding transaction.
	 */
	public void commit() {
		// Not using transactions yet.
	}
	
	
	/**
	 * Releases all database resources. This method is guaranteed not to throw
	 * transactions and should always be called in a finally block whenever this
	 * class is used.
	 */
	public void release() {
		if (connection != null) {
			try {
				connection.close();
				
			} catch (SQLException e) {
				// Do nothing.
			}
			
			connection = null;
		}
	}
	
	
	/**
	 * Enforces cleanup of any remaining resources during garbage collection.
	 * This is a safeguard and should not be required if release is called
	 * appropriately.
	 */
	@Override
	protected void finalize() throws Throwable {
		release();
		
		super.finalize();
	}
}
package test.sample.javaee;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAResource;

public class JavaEEServlet extends HttpServlet implements Servlet {

	private static final long serialVersionUID = 1L;
	@SuppressWarnings("unused")
	private XAResource xaResource;
	@SuppressWarnings("unused")
	private TransactionSynchronizationRegistry transactionSynchronizationRegistry;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		super.doGet(req, resp);
	}

}

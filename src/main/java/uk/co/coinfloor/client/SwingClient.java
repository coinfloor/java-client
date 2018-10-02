/*
 * Created on Mar 8, 2014
 */
package uk.co.coinfloor.client;

import java.awt.AWTEvent;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;

import uk.co.coinfloor.api.Callback;
import uk.co.coinfloor.api.Coinfloor;
import uk.co.coinfloor.api.Coinfloor.MarketOrderEstimate;
import uk.co.coinfloor.api.Coinfloor.OrderInfo;
import uk.co.coinfloor.api.Coinfloor.TickerInfo;

@SuppressWarnings("serial")
public class SwingClient extends JPanel {

	interface BalanceListener extends EventListener {

		void balanceChanged(AssetType assetType, long balance);

	}

	interface OrderListener extends EventListener {

		void orderOpened(long id, boolean own, AssetType base, AssetType counter, long quantity, long price);

		void orderMatched(long id, boolean own, AssetType base, AssetType counter, long quantity, long remaining);

		void orderClosed(long id, boolean own, AssetType base, AssetType counter, long quantity, long price);

	}

	interface TickerListener extends EventListener {

		void tickerChanged(AssetType base, AssetType counter, long last, long bid, long ask, long low, long high, long volume);

	}

	static class SwingCallback<V> implements Callback<V> {

		final Component parent;
		final String errorPreamble;

		SwingCallback(Component parent, String errorPreamble) {
			this.parent = parent;
			this.errorPreamble = errorPreamble;
		}

		@Override
		public final void operationCompleted(final V result) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					completed(result);
				}

			});
		}

		@Override
		public final void operationFailed(final Exception exception) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					failed(exception);
				}

			});
		}

		void completed(V result) {
		}

		void failed(Exception exception) {
			JOptionPane.showMessageDialog(parent, errorPreamble == null ? exception.toString() : errorPreamble + "\n\n" + exception, "Error", JOptionPane.WARNING_MESSAGE);
		}

	}

	static abstract class SwingTask<V> extends SwingCallback<V> implements Callable<V> {

		private transient Cursor origCursor;

		SwingTask(Component parent, String errorPreamble) {
			super(parent, errorPreamble);
		}

		final void execute(Executor executor) {
			if (SwingUtilities.isEventDispatchThread()) {
				before();
			}
			else {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						before();
					}

				});
			}
			executor.execute(new Runnable() {

				@Override
				public void run() {
					try {
						V result;
						try {
							result = call();
						}
						catch (Exception e) {
							operationFailed(e);
							return;
						}
						operationCompleted(result);
					}
					finally {
						SwingUtilities.invokeLater(new Runnable() {

							@Override
							public void run() {
								after();
							}

						});
					}
				}

			});
		}

		void before() {
			origCursor = parent.getCursor();
			parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		}

		@Override
		void completed(V result) {
		}

		void after() {
			parent.setCursor(origCursor);
		}

	}

	abstract class MarketPanel extends JPanel implements PropertyChangeListener {

		MarketPanel(LayoutManager layout) {
			super(layout);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			SwingClient.this.addPropertyChangeListener("market", this);
		}

		@Override
		public void removeNotify() {
			SwingClient.this.removePropertyChangeListener("market", this);
			super.removeNotify();
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (evt.getPropertyName() == "market") {
				marketChanged((AssetType.Pair) evt.getOldValue(), (AssetType.Pair) evt.getNewValue());
			}
		}

		abstract void marketChanged(AssetType.Pair oldMarket, AssetType.Pair newMarket);

	}

	class TickerPanel extends MarketPanel implements TickerListener {

		final JLabel lastLabel, bidLabel, askLabel, lowLabel, highLabel, volumeLabel;

		TickerPanel() {
			super(new GridLayout(2, 6, 10, 0));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 20, 10, 20)));

			JLabel lastLabelLabel = new JLabel("Last:", SwingConstants.CENTER);
			lastLabelLabel.setLabelFor(lastLabel = new JLabel("\u2014", SwingConstants.CENTER));

			JLabel bidLabelLabel = new JLabel("Bid:", SwingConstants.CENTER);
			bidLabelLabel.setLabelFor(bidLabel = new JLabel("\u2014", SwingConstants.CENTER));

			JLabel askLabelLabel = new JLabel("Ask:", SwingConstants.CENTER);
			askLabelLabel.setLabelFor(askLabel = new JLabel("\u2014", SwingConstants.CENTER));

			JLabel lowLabelLabel = new JLabel("Low:", SwingConstants.CENTER);
			lowLabelLabel.setLabelFor(lowLabel = new JLabel("\u2014", SwingConstants.CENTER));

			JLabel highLabelLabel = new JLabel("High:", SwingConstants.CENTER);
			highLabelLabel.setLabelFor(highLabel = new JLabel("\u2014", SwingConstants.CENTER));

			JLabel volumeLabelLabel = new JLabel("Volume:", SwingConstants.CENTER);
			volumeLabelLabel.setLabelFor(volumeLabel = new JLabel("\u2014", SwingConstants.CENTER));

			add(lastLabelLabel);
			add(bidLabelLabel);
			add(askLabelLabel);
			add(lowLabelLabel);
			add(highLabelLabel);
			add(volumeLabelLabel);
			add(lastLabel);
			add(bidLabel);
			add(askLabel);
			add(lowLabel);
			add(highLabel);
			add(volumeLabel);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			addTickerListener(this);
		}

		@Override
		public void removeNotify() {
			removeTickerListener(this);
			super.removeNotify();
		}

		@Override
		public void tickerChanged(AssetType base, AssetType counter, long last, long bid, long ask, long low, long high, long volume) {
			if (base == market.base && counter == market.counter) {
				lastLabel.setText(last < 0 ? "\u2014" : counter.format(BigDecimal.valueOf(last, counter.scale)));
				bidLabel.setText(bid < 0 ? "\u2014" : counter.format(BigDecimal.valueOf(bid, counter.scale)));
				askLabel.setText(ask < 0 ? "\u2014" : counter.format(BigDecimal.valueOf(ask, counter.scale)));
				lowLabel.setText(low < 0 ? "\u2014" : counter.format(BigDecimal.valueOf(low, counter.scale)));
				highLabel.setText(high < 0 ? "\u2014" : counter.format(BigDecimal.valueOf(high, counter.scale)));
				volumeLabel.setText(volume < 0 ? "\u2014" : base.format(BigDecimal.valueOf(volume, base.scale)));
			}
		}

		void reset() {
			lastLabel.setText("\u2014");
			bidLabel.setText("\u2014");
			askLabel.setText("\u2014");
			lowLabel.setText("\u2014");
			highLabel.setText("\u2014");
			volumeLabel.setText("\u2014");
		}

		@Override
		void marketChanged(AssetType.Pair oldMarket, AssetType.Pair newMarket) {
			if (oldMarket != null) {
				unsubscribe(oldMarket);
			}
			if (newMarket != null) {
				subscribe(newMarket);
			}
		}

		void subscribe(final AssetType.Pair market) {
			SwingCallback<TickerInfo> callback = new SwingCallback<TickerInfo>(SwingClient.this, "Failed to subscribe to ticker.") {

				@Override
				void completed(TickerInfo ticker) {
					tickerChanged(market.base, market.counter, ticker.last, ticker.bid, ticker.ask, ticker.low, ticker.high, ticker.volume);
				}

			};
			try {
				coinfloor.watchTickerAsync(market.base.code, market.counter.code, true, callback);
			}
			catch (IOException e) {
				callback.failed(e);
			}
		}

		void unsubscribe(AssetType.Pair market) {
			reset();
			try {
				coinfloor.watchTickerAsync(market.base.code, market.counter.code, false);
			}
			catch (IOException ignored) {
			}
		}

	}

	class BalancesPanel extends MarketPanel implements BalanceListener {

		final JLabel baseBalanceLabel, counterBalanceLabel;

		BalancesPanel() {
			super(new GridLayout(1, 2, 10, 0));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 20, 10, 20)));
			add(counterBalanceLabel = new JLabel("\u2014", SwingConstants.CENTER));
			add(baseBalanceLabel = new JLabel("\u2014", SwingConstants.CENTER));
		}

		@Override
		public void addNotify() {
			super.addNotify();
			addBalanceListener(this);
		}

		@Override
		public void removeNotify() {
			removeBalanceListener(this);
			super.removeNotify();
		}

		@Override
		public void balanceChanged(AssetType assetType, long balance) {
			JLabel label;
			if (assetType == market.base) {
				label = baseBalanceLabel;
			}
			else if (assetType == market.counter) {
				label = counterBalanceLabel;
			}
			else {
				return;
			}
			label.setText(balance < 0 ? "\u2014" : assetType.format(BigDecimal.valueOf(balance, assetType.scale)));
		}

		void reset() {
			baseBalanceLabel.setText("\u2014");
			counterBalanceLabel.setText("\u2014");
		}

		@Override
		void marketChanged(AssetType.Pair oldMarket, AssetType.Pair newMarket) {
			if (oldMarket != null) {
				unsubscribe(oldMarket);
			}
			if (newMarket != null) {
				subscribe(newMarket);
			}
		}

		void subscribe(AssetType.Pair market) {
			SwingCallback<Map<Integer, Long>> callback = new SwingCallback<Map<Integer, Long>>(this, "Failed to retrieve balances.") {

				@Override
				void completed(Map<Integer, Long> balances) {
					for (Map.Entry<Integer, Long> entry : balances.entrySet()) {
						balanceChanged(AssetType.forCode(entry.getKey()), entry.getValue());
					}
				}

			};
			try {
				coinfloor.getBalancesAsync(callback);
			}
			catch (IOException e) {
				callback.failed(e);
			}
		}

		void unsubscribe(AssetType.Pair market) {
			reset();
		}

	}

	class AuthPanel extends JPanel {

		final JComboBox uriComboBox;
		final JTextField userIDField;
		final JPasswordField cookieField, passphraseField;
		final JButton connectButton;

		AuthPanel() {
			super(null);

			JLabel uriLabel = new JLabel("URI:");
			uriLabel.setLabelFor(uriComboBox = new JComboBox(new Object[] { "wss://api.coinfloor.co.uk/", "wss://api.cfe.gi/" }));
			uriComboBox.setEditable(true);

			JLabel userIDLabel = new JLabel("User ID:");
			userIDLabel.setLabelFor(userIDField = new JTextField(10));

			JLabel cookieLabel = new JLabel("Cookie:");
			cookieLabel.setLabelFor(cookieField = new JPasswordField(20));

			JLabel passphraseLabel = new JLabel("Passphrase:");
			passphraseLabel.setLabelFor(passphraseField = new JPasswordField(20));

			connectButton = new JButton("Connect");

			connectButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent event) {
					final URI uri;
					try {
						uri = new URI((String) uriComboBox.getSelectedItem());
					}
					catch (URISyntaxException e) {
						JOptionPane.showMessageDialog(AuthPanel.this, "Invalid URI.", "Error", JOptionPane.WARNING_MESSAGE);
						uriComboBox.requestFocusInWindow();
						return;
					}
					final long userID;
					try {
						userID = Long.parseLong(userIDField.getText());
					}
					catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(AuthPanel.this, "Invalid user ID.", "Error", JOptionPane.WARNING_MESSAGE);
						userIDField.requestFocusInWindow();
						return;
					}
					final String cookie = new String(cookieField.getPassword());
					if (cookie.isEmpty()) {
						JOptionPane.showMessageDialog(AuthPanel.this, "Cookie is required.", "Error", JOptionPane.WARNING_MESSAGE);
						cookieField.requestFocusInWindow();
						return;
					}
					final String passphrase = new String(passphraseField.getPassword());
					uriComboBox.setEnabled(false);
					userIDField.setEnabled(false);
					cookieField.setEnabled(false);
					passphraseField.setEnabled(false);
					connectButton.setEnabled(false);
					new SwingTask<Void>(AuthPanel.this, "Failed to connect to Coinfloor server.") {

						@Override
						public Void call() throws IOException {
							if (!connected) {
								coinfloor.connect(uri);
							}
							coinfloor.authenticateAsync(userID, cookie, passphrase, new SwingCallback<Void>(AuthPanel.this, "Authentication failed.") {

								@Override
								void completed(Void result) {
									setAuthenticated(true);
									cardLayout.next(SwingClient.this);
								}

								@Override
								void failed(Exception exception) {
									super.failed(exception);
									userIDField.setEnabled(true);
									cookieField.setEnabled(true);
									passphraseField.setEnabled(true);
									connectButton.setEnabled(true);
								}

							});
							return null;
						}

						@Override
						void completed(Void result) {
							super.completed(result);
							setConnected(true);
							connectButton.setText("Authenticate");
						}

						@Override
						void failed(Exception exception) {
							super.failed(exception);
							uriComboBox.setEnabled(true);
							userIDField.setEnabled(true);
							cookieField.setEnabled(true);
							passphraseField.setEnabled(true);
							connectButton.setEnabled(true);
						}

					}.execute(executor);
				}

			});

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createParallelGroup(Alignment.TRAILING)
							.addComponent(uriLabel)
							.addComponent(userIDLabel)
							.addComponent(cookieLabel)
							.addComponent(passphraseLabel))
					.addGroup(layout.createParallelGroup()
							.addComponent(uriComboBox, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(userIDField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(cookieField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(passphraseField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(connectButton))
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			layout.setVerticalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(uriLabel)
							.addComponent(uriComboBox))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(userIDLabel)
							.addComponent(userIDField))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(cookieLabel)
							.addComponent(cookieField))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(passphraseLabel)
							.addComponent(passphraseField))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(connectButton)
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			// @formatter:on
			setLayout(layout);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			userIDField.requestFocusInWindow();
			getRootPane().setDefaultButton(connectButton);
		}

	}

	class TradePanel extends JPanel implements PropertyChangeListener {

		final OrderPanel orderPanel = new OrderPanel();
		final JComboBox marketComboBox;
		final MyOrdersPanel myOrdersPanel = new MyOrdersPanel();
		final OrdersPanel ordersPanel = new OrdersPanel();

		TradePanel() {
			super(null);

			JLabel marketLabel = new JLabel("Market:");
			marketLabel.setLabelFor(marketComboBox = new JComboBox(new AssetType.Pair[] { new AssetType.Pair(AssetType.XBT, AssetType.EUR), new AssetType.Pair(AssetType.XBT, AssetType.GBP), new AssetType.Pair(AssetType.XBT, AssetType.USD), new AssetType.Pair(AssetType.XBT, AssetType.PLN) }));
			marketComboBox.setSelectedItem(null);
			marketComboBox.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					setMarket((AssetType.Pair) marketComboBox.getSelectedItem());
				}

			});

			JSeparator separator = new JSeparator();

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateContainerGaps(true);
			layout.setAutoCreateGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(layout.createParallelGroup()
					.addGroup(layout.createSequentialGroup()
							.addComponent(orderPanel)
							.addPreferredGap(ComponentPlacement.UNRELATED)
							.addGroup(layout.createParallelGroup(Alignment.CENTER)
									.addComponent(marketLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
									.addComponent(marketComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)))
					.addComponent(myOrdersPanel)
					.addComponent(separator)
					.addComponent(ordersPanel));
			layout.setVerticalGroup(layout.createSequentialGroup()
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
							.addComponent(orderPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
							.addGroup(layout.createSequentialGroup()
									.addComponent(marketLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
									.addComponent(marketComboBox, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(myOrdersPanel)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(separator, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(ordersPanel));
			// @formatter:on
			setLayout(layout);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			SwingClient.this.addPropertyChangeListener("authenticated", this);
		}

		@Override
		public void removeNotify() {
			SwingClient.this.removePropertyChangeListener("authenticated", this);
			super.removeNotify();
		}

		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if (evt.getPropertyName() == "authenticated") {
				marketComboBox.setSelectedIndex(1);
			}
		}

	}

	class OrderPanel extends JTabbedPane {

		final LimitOrderPanel limitOrderPanel;
		final MarketOrderPanel marketOrderPanel;

		OrderPanel() {
			addTab("Limit Order", limitOrderPanel = new LimitOrderPanel());
			addTab("Market Order", marketOrderPanel = new MarketOrderPanel());

			addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent e) {
					JButton defaultButton = null;
					Component selectedComponent = getSelectedComponent();
					if (selectedComponent == limitOrderPanel) {
						defaultButton = limitOrderPanel.submitButton;
						limitOrderPanel.priceField.requestFocusInWindow();
					}
					else if (selectedComponent == marketOrderPanel) {
						defaultButton = marketOrderPanel.executeButton;
						marketOrderPanel.amountField.requestFocusInWindow();
					}
					getRootPane().setDefaultButton(defaultButton);
				}

			});
		}

	}

	class MarketOrderPanel extends MarketPanel {

		final Timer timer = new Timer(true);

		final JRadioButton buyRadioButton, sellRadioButton, spendRadioButton, getRadioButton;
		final JLabel amountLabel, amountSymbolLabel;
		final JTextField amountField;
		final JLabel estimatedQuantityLabelLabel, estimatedTotalLabelLabel, averagePriceLabelLabel;
		final JLabel estimatedQuantityLabel, estimatedTotalLabel, averagePriceLabel;
		final JButton executeButton;

		TimerTask timerTask;

		MarketOrderPanel() {
			super(null);
			setOpaque(false);

			ButtonGroup buttonGroup = new ButtonGroup();
			buttonGroup.add(buyRadioButton = new JRadioButton("Buy"));
			buyRadioButton.setOpaque(false);
			buttonGroup.add(sellRadioButton = new JRadioButton("Sell"));
			sellRadioButton.setOpaque(false);
			buttonGroup.add(spendRadioButton = new JRadioButton("Spend"));
			spendRadioButton.setOpaque(false);
			buttonGroup.add(getRadioButton = new JRadioButton("Get"));
			getRadioButton.setOpaque(false);
			buyRadioButton.setSelected(true);

			amountLabel = new JLabel("Quantity:");
			amountSymbolLabel = new JLabel();
			amountLabel.setLabelFor(amountField = new JTextField(10));

			estimatedQuantityLabelLabel = new JLabel("Estimated Quantity:");
			estimatedQuantityLabelLabel.setLabelFor(estimatedQuantityLabel = new JLabel("\u2014"));
			estimatedTotalLabelLabel = new JLabel("Estimated Total:");
			estimatedTotalLabelLabel.setLabelFor(estimatedTotalLabel = new JLabel("\u2014"));
			averagePriceLabelLabel = new JLabel("Average Price:");
			averagePriceLabelLabel.setLabelFor(averagePriceLabel = new JLabel("\u2014"));

			executeButton = new JButton("Execute");
			executeButton.setEnabled(false);

			ActionListener radioButtonListener = new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					update();
				}

			};
			buyRadioButton.addActionListener(radioButtonListener);
			sellRadioButton.addActionListener(radioButtonListener);
			spendRadioButton.addActionListener(radioButtonListener);
			getRadioButton.addActionListener(radioButtonListener);

			amountField.getDocument().addDocumentListener(new DocumentListener() {

				@Override
				public void insertUpdate(DocumentEvent e) {
					inputChanged();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					inputChanged();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					inputChanged();
				}

			});

			executeButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent event) {
					BigDecimal amount = getAmount(amountField);
					SwingCallback<Long> callback = new SwingCallback<Long>(MarketOrderPanel.this, "Failed to execute market order.") {

						@Override
						void completed(Long remaining) {
							JOptionPane.showMessageDialog(MarketOrderPanel.this, "Your market order was executed.", "Market Order", JOptionPane.INFORMATION_MESSAGE);
							executeButton.setEnabled(true);
						}

						@Override
						void failed(Exception exception) {
							super.failed(exception);
							executeButton.setEnabled(true);
						}

					};
					executeButton.setEnabled(false);
					try {
						AssetType base = market.base, counter = market.counter;
						if (buyRadioButton.isSelected()) {
							coinfloor.executeBaseMarketOrderAsync(base.code, counter.code, amount.movePointRight(base.scale).longValue(), 0, callback);
						}
						else if (sellRadioButton.isSelected()) {
							coinfloor.executeBaseMarketOrderAsync(base.code, counter.code, -amount.movePointRight(base.scale).longValue(), 0, callback);
						}
						else if (spendRadioButton.isSelected()) {
							coinfloor.executeCounterMarketOrderAsync(base.code, counter.code, amount.movePointRight(counter.scale).longValue(), 0, callback);
						}
						else if (getRadioButton.isSelected()) {
							coinfloor.executeCounterMarketOrderAsync(base.code, counter.code, -amount.movePointRight(counter.scale).longValue(), 0, callback);
						}
					}
					catch (IOException e) {
						callback.failed(e);
					}
				}

			});

			updateLayout(null);
		}

		void updateLayout(AssetType amountAsset) {
			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateContainerGaps(true);
			layout.setAutoCreateGaps(true);
			SequentialGroup amountHGroup;
			// @formatter:off
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
							.addGroup(layout.createSequentialGroup()
									.addComponent(buyRadioButton)
									.addComponent(sellRadioButton)
									.addComponent(spendRadioButton)
									.addComponent(getRadioButton))
							.addGroup(layout.createSequentialGroup()
									.addComponent(amountLabel)
									.addGroup(amountHGroup = layout.createSequentialGroup())
									.addPreferredGap(ComponentPlacement.UNRELATED)
									.addComponent(executeButton)))
					.addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createSequentialGroup()
							.addGroup(layout.createParallelGroup(Alignment.TRAILING)
									.addComponent(estimatedQuantityLabelLabel)
									.addComponent(estimatedTotalLabelLabel)
									.addComponent(averagePriceLabelLabel))
							.addGroup(layout.createParallelGroup(Alignment.TRAILING)
									.addComponent(estimatedQuantityLabel)
									.addComponent(estimatedTotalLabel)
									.addComponent(averagePriceLabel)))
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			layout.setVerticalGroup(layout.createParallelGroup(Alignment.CENTER)
					.addGroup(layout.createSequentialGroup()
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(buyRadioButton)
									.addComponent(sellRadioButton)
									.addComponent(spendRadioButton)
									.addComponent(getRadioButton))
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(amountLabel)
									.addComponent(amountSymbolLabel)
									.addComponent(amountField)
									.addComponent(executeButton)))
					.addGroup(layout.createSequentialGroup()
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(estimatedQuantityLabelLabel)
									.addComponent(estimatedQuantityLabel))
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(estimatedTotalLabelLabel)
									.addComponent(estimatedTotalLabel))
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(averagePriceLabelLabel)
									.addComponent(averagePriceLabel))));
			// @formatter:on
			if (amountAsset != null && amountAsset.symbolAfter) {
				amountHGroup.addComponent(amountField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).addGap(0).addComponent(amountSymbolLabel);
			}
			else {
				amountHGroup.addComponent(amountSymbolLabel).addGap(0).addComponent(amountField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
			}
			setLayout(layout);
		}

		void inputChanged() {
			if (timerTask != null) {
				timerTask.cancel();
				timerTask = null;
			}
			estimatedQuantityLabel.setText("\u2014");
			estimatedTotalLabel.setText("\u2014");
			averagePriceLabel.setText("\u2014");
			executeButton.setEnabled(false);
			BigDecimal amountBig = getAmount(amountField);
			if (amountBig == null || amountBig.signum() <= 0) {
				return;
			}
			final boolean useBase;
			final long amount;
			if (buyRadioButton.isSelected()) {
				useBase = true;
				amount = amountBig.movePointRight(market.base.scale).longValue();
			}
			else if (sellRadioButton.isSelected()) {
				useBase = true;
				amount = -amountBig.movePointRight(market.base.scale).longValue();
			}
			else if (spendRadioButton.isSelected()) {
				useBase = false;
				amount = amountBig.movePointRight(market.counter.scale).longValue();
			}
			else if (getRadioButton.isSelected()) {
				useBase = false;
				amount = -amountBig.movePointRight(market.counter.scale).longValue();
			}
			else {
				return;
			}
			executeButton.setEnabled(true);
			getRootPane().setDefaultButton(executeButton);
			timer.schedule(timerTask = new TimerTask() {

				@Override
				public void run() {
					Callback<MarketOrderEstimate> callback = new SwingCallback<MarketOrderEstimate>(MarketOrderPanel.this, null) {

						@Override
						void completed(MarketOrderEstimate estimate) {
							AssetType base = market.base, counter = market.counter;
							BigDecimal estimatedQuantity = BigDecimal.valueOf(estimate.quantity, base.scale);
							estimatedQuantityLabel.setText(base.format(estimatedQuantity));
							BigDecimal estimatedTotal = BigDecimal.valueOf(estimate.total, counter.scale);
							estimatedTotalLabel.setText(counter.format(estimatedTotal));
							BigDecimal averagePrice = estimatedTotal.divide(estimatedQuantity, counter.scale, RoundingMode.HALF_EVEN);
							averagePriceLabel.setText(counter.format(averagePrice));
						}

						@Override
						void failed(Exception exception) {
							// no-op
						}

					};
					try {
						if (useBase) {
							coinfloor.estimateBaseMarketOrderAsync(market.base.code, market.counter.code, amount, callback);
						}
						else {
							coinfloor.estimateCounterMarketOrderAsync(market.base.code, market.counter.code, amount, callback);
						}
					}
					catch (IOException ignored) {
					}
				}

			}, 1000, 5000);
		}

		@Override
		void marketChanged(AssetType.Pair oldMarket, AssetType.Pair newMarket) {
			update();
		}

		void update() {
			AssetType amountAsset = null;
			if (buyRadioButton.isSelected() || sellRadioButton.isSelected()) {
				amountLabel.setText("Quantity:");
				amountSymbolLabel.setText((amountAsset = market.base).symbol);
			}
			else if (spendRadioButton.isSelected() || getRadioButton.isSelected()) {
				amountLabel.setText("Total:");
				amountSymbolLabel.setText((amountAsset = market.counter).symbol);
			}
			amountField.setText(null);
			updateLayout(amountAsset);
			inputChanged();
		}

	}

	class LimitOrderPanel extends MarketPanel {

		final JRadioButton bidRadioButton, askRadioButton;
		final JLabel priceLabel, quantityLabel;
		final JLabel priceSymbolLabel, quantitySymbolLabel;
		final JTextField priceField, quantityField;
		final JCheckBox cancelOnDisconnectCheckBox;
		final JButton submitButton;

		LimitOrderPanel() {
			super(null);
			setOpaque(false);

			ButtonGroup buttonGroup = new ButtonGroup();
			buttonGroup.add(bidRadioButton = new JRadioButton("Bid"));
			bidRadioButton.setOpaque(false);
			buttonGroup.add(askRadioButton = new JRadioButton("Ask"));
			askRadioButton.setOpaque(false);
			bidRadioButton.setSelected(true);

			priceLabel = new JLabel("Price:");
			priceSymbolLabel = new JLabel();
			priceLabel.setLabelFor(priceField = new JTextField(10));

			quantityLabel = new JLabel("Quantity:");
			quantitySymbolLabel = new JLabel();
			quantityLabel.setLabelFor(quantityField = new JTextField(10));

			cancelOnDisconnectCheckBox = new JCheckBox("Cancel order on disconnect");

			submitButton = new JButton("Submit");
			submitButton.setEnabled(false);

			DocumentListener documentListener = new DocumentListener() {

				@Override
				public void insertUpdate(DocumentEvent e) {
					inputChanged();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					inputChanged();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					inputChanged();
				}

			};
			priceField.getDocument().addDocumentListener(documentListener);
			quantityField.getDocument().addDocumentListener(documentListener);

			submitButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent event) {
					long quantity = getAmount(quantityField).movePointRight(market.base.scale).longValue();
					final long fQuantity;
					if (bidRadioButton.isSelected()) {
						fQuantity = quantity;
					}
					else if (askRadioButton.isSelected()) {
						fQuantity = -quantity;
					}
					else {
						return;
					}
					final long price = getAmount(priceField).movePointRight(market.counter.scale).longValue();
					SwingCallback<Long> callback = new SwingCallback<Long>(LimitOrderPanel.this, "Failed to submit limit order.") {

						@Override
						void completed(Long result) {
							super.completed(result);
							submitButton.setEnabled(true);
						}

						@Override
						void failed(Exception exception) {
							super.failed(exception);
							submitButton.setEnabled(true);
						}

					};
					submitButton.setEnabled(false);
					try {
						coinfloor.placeLimitOrderAsync(market.base.code, market.counter.code, fQuantity, price, 0, !cancelOnDisconnectCheckBox.isSelected(), callback);
					}
					catch (IOException e) {
						callback.failed(e);
					}
				}

			});

			updateLayout();
		}

		void updateLayout() {
			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateContainerGaps(true);
			layout.setAutoCreateGaps(true);
			SequentialGroup priceHGroup, quantityHGroup;
			// @formatter:off
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
						.addGroup(layout.createSequentialGroup()
								.addComponent(bidRadioButton)
								.addComponent(askRadioButton))
						.addGroup(layout.createSequentialGroup()
								.addComponent(priceLabel)
								.addGroup(priceHGroup = layout.createSequentialGroup())
								.addPreferredGap(ComponentPlacement.UNRELATED)
								.addComponent(quantityLabel)
								.addGroup(quantityHGroup = layout.createSequentialGroup())
								.addPreferredGap(ComponentPlacement.UNRELATED)
								.addComponent(submitButton)))
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			layout.setVerticalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(bidRadioButton)
							.addComponent(askRadioButton))
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(priceLabel)
							.addComponent(priceSymbolLabel)
							.addComponent(priceField)
							.addComponent(quantityLabel)
							.addComponent(quantitySymbolLabel)
							.addComponent(quantityField)
							.addComponent(submitButton))
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			// @formatter:on
			if (market != null && market.counter.symbolAfter) {
				priceHGroup.addComponent(priceField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).addGap(0).addComponent(priceSymbolLabel);
			}
			else {
				priceHGroup.addComponent(priceSymbolLabel).addGap(0).addComponent(priceField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
			}
			if (market != null && market.base.symbolAfter) {
				quantityHGroup.addComponent(quantityField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE).addGap(0).addComponent(quantitySymbolLabel);
			}
			else {
				quantityHGroup.addComponent(quantitySymbolLabel).addGap(0).addComponent(quantityField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE);
			}
			setLayout(layout);
		}

		void inputChanged() {
			submitButton.setEnabled(false);
			BigDecimal quantity, price;
			if ((quantity = getAmount(quantityField)) == null || quantity.signum() < 0 || (price = getAmount(priceField)) == null || price.signum() < 0) {
				return;
			}
			submitButton.setEnabled(true);
			getRootPane().setDefaultButton(submitButton);
		}

		@Override
		void marketChanged(AssetType.Pair oldMarket, AssetType.Pair newMarket) {
			priceSymbolLabel.setText(newMarket.counter.symbol);
			priceField.setText(null);
			quantitySymbolLabel.setText(newMarket.base.symbol);
			quantityField.setText(null);
			updateLayout();
		}

	}

	class OrdersPanel extends MarketPanel implements OrderListener {

		final OrdersTable bidsTable, asksTable;

		Group horizontalGroup, verticalGroup;

		OrdersPanel() {
			this("All Bids", "All Asks");
		}

		OrdersPanel(String bidsLabelText, String asksLabelText) {
			super(null);

			JLabel bidsLabel = new JLabel(bidsLabelText);
			bidsLabel.setLabelFor(bidsTable = new OrdersTable());
			bidsTable.setRowSelectionAllowed(false);
			bidsTable.getRowSorter().setSortKeys(Arrays.asList(new SortKey(0, SortOrder.DESCENDING)));
			JScrollPane bidsTableScrollPane = new JScrollPane(bidsTable);

			JLabel asksLabel = new JLabel(asksLabelText);
			asksLabel.setLabelFor(asksTable = new OrdersTable());
			asksTable.setRowSelectionAllowed(false);
			asksTable.getRowSorter().setSortKeys(Arrays.asList(new SortKey(0, SortOrder.ASCENDING)));
			JScrollPane asksTableScrollPane = new JScrollPane(asksTable);

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(horizontalGroup = layout.createSequentialGroup()
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
							.addComponent(bidsLabel)
							.addComponent(bidsTableScrollPane, GroupLayout.DEFAULT_SIZE, 400, GroupLayout.DEFAULT_SIZE))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
							.addComponent(asksLabel)
							.addComponent(asksTableScrollPane, GroupLayout.DEFAULT_SIZE, 400, GroupLayout.DEFAULT_SIZE)));
			layout.setVerticalGroup(verticalGroup = layout.createSequentialGroup()
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(bidsLabel)
							.addComponent(asksLabel))
					.addGroup(layout.createParallelGroup()
							.addComponent(bidsTableScrollPane, GroupLayout.DEFAULT_SIZE, 200, GroupLayout.DEFAULT_SIZE)
							.addComponent(asksTableScrollPane, GroupLayout.DEFAULT_SIZE, 200, GroupLayout.DEFAULT_SIZE)));
			// @formatter:on
			setLayout(layout);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			addOrderListener(this);
		}

		@Override
		public void removeNotify() {
			removeOrderListener(this);
			super.removeNotify();
		}

		@Override
		public void orderOpened(long id, boolean own, AssetType base, AssetType counter, long quantity, long price) {
			if (base == market.base && counter == market.counter) {
				addOrder(id, price, quantity, 0, quantity);
			}
		}

		@Override
		public void orderMatched(long id, boolean own, AssetType base, AssetType counter, long quantity, long remaining) {
			if (base == market.base && counter == market.counter) {
				updateOrder(id, quantity, remaining);
			}
		}

		@Override
		public void orderClosed(long id, boolean own, AssetType base, AssetType counter, long quantity, long price) {
			if (base == market.base && counter == market.counter) {
				removeOrder(id);
			}
		}

		void addOrder(long id, long price, long quantity, long filled, long remaining) {
			if (quantity > 0) {
				addOrder(bidsTable, id, price, quantity, filled, remaining);
			}
			else if (quantity < 0) {
				addOrder(asksTable, id, price, -quantity, filled, -remaining);
			}
		}

		private void addOrder(JTable table, long id, long price, long quantity, long filled, long remaining) {
			OrdersTableModel model = (OrdersTableModel) table.getModel();
			int index = model.orders.size();
			model.orders.add(new Order(id, price, quantity, filled, remaining));
			model.fireTableRowsInserted(index, index);
		}

		Long updateOrder(long id, long filledDelta, long remaining) {
			if (remaining > 0) {
				return updateOrder(bidsTable, id, filledDelta, remaining);
			}
			if (remaining < 0) {
				return updateOrder(asksTable, id, filledDelta, -remaining);
			}
			Long ret;
			return (ret = updateOrder(bidsTable, id, filledDelta, 0)) == null ? updateOrder(asksTable, id, filledDelta, 0) : ret;
		}

		private Long updateOrder(JTable table, long id, long filledDelta, long remaining) {
			OrdersTableModel model = (OrdersTableModel) table.getModel();
			for (ListIterator<Order> it = model.orders.listIterator(); it.hasNext();) {
				Order order = it.next();
				if (order.id == id) {
					order.filled += filledDelta;
					Long ret = order.remaining;
					order.remaining = remaining;
					int index = it.previousIndex();
					model.fireTableRowsUpdated(index, index);
					return ret;
				}
			}
			return null;
		}

		Long removeOrder(long id) {
			Long ret;
			return (ret = removeOrder(bidsTable, id)) == null ? removeOrder(asksTable, id) : ret;
		}

		private Long removeOrder(OrdersTable table, long id) {
			OrdersTableModel model = table.getModel();
			for (ListIterator<Order> it = model.orders.listIterator(); it.hasNext();) {
				Order order = it.next();
				if (order.id == id) {
					int index = it.previousIndex();
					it.remove();
					model.fireTableRowsDeleted(index, index);
					return order.remaining;
				}
			}
			return null;
		}

		void removeAllOrders() {
			removeAllOrders(bidsTable);
			removeAllOrders(asksTable);
		}

		private void removeAllOrders(OrdersTable table) {
			OrdersTableModel model = table.getModel();
			model.orders.clear();
			model.fireTableDataChanged();
		}

		@Override
		void marketChanged(AssetType.Pair oldMarket, AssetType.Pair newMarket) {
			if (oldMarket != null) {
				unsubscribe(oldMarket);
			}
			if (newMarket != null) {
				subscribe(newMarket);
			}
		}

		void subscribe(AssetType.Pair market) {
			SwingCallback<Map<Long, OrderInfo>> callback = new SwingCallback<Map<Long, OrderInfo>>(this, "Failed to subscribe to orders.") {

				@Override
				void completed(Map<Long, OrderInfo> orders) {
					removeAllOrders();
					for (Map.Entry<Long, OrderInfo> entry : orders.entrySet()) {
						OrderInfo info = entry.getValue();
						addOrder(entry.getKey(), info.price, info.quantity, 0, info.quantity);
					}
				}

			};
			try {
				coinfloor.watchOrdersAsync(market.base.code, market.counter.code, true, callback);
			}
			catch (IOException e) {
				callback.failed(e);
			}
		}

		void unsubscribe(AssetType.Pair market) {
			removeAllOrders();
			try {
				coinfloor.watchOrdersAsync(market.base.code, market.counter.code, false);
			}
			catch (IOException ignored) {
			}
		}

	}

	class MyOrdersPanel extends OrdersPanel {

		final JButton cancelAllButton, cancelSelectedButton, clearClosedButton;

		long totalRemaining;

		MyOrdersPanel() {
			super("My Bids", "My Asks");

			bidsTable.setRowSelectionAllowed(true);
			asksTable.setRowSelectionAllowed(true);

			cancelAllButton = new JButton("Cancel All Orders");
			cancelAllButton.setEnabled(false);

			cancelSelectedButton = new JButton("Cancel Selected Orders");
			cancelSelectedButton.setEnabled(false);

			clearClosedButton = new JButton("Clear Closed Orders");
			clearClosedButton.setEnabled(false);

			ListSelectionListener selectionListener = new ListSelectionListener() {

				@Override
				public void valueChanged(ListSelectionEvent e) {
					if (e.getValueIsAdjusting()) {
						AWTEvent currentEvent = EventQueue.getCurrentEvent();
						if (currentEvent instanceof InputEvent && (((InputEvent) currentEvent).getModifiersEx() & (InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK)) == 0) {
							(e.getSource() == bidsTable.getSelectionModel() ? asksTable : bidsTable).clearSelection();
						}
					}
					else {
						cancelSelectedButton.setEnabled(bidsTable.getSelectedRowCount() > 0 || asksTable.getSelectedRowCount() > 0);
					}
				}

			};
			bidsTable.getSelectionModel().addListSelectionListener(selectionListener);
			asksTable.getSelectionModel().addListSelectionListener(selectionListener);

			cancelAllButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent evt) {
					SwingCallback<Map<Long, OrderInfo>> callback = new SwingCallback<Map<Long, OrderInfo>>(MyOrdersPanel.this, "Failed to cancel orders.") {

						@Override
						void completed(Map<Long, OrderInfo> info) {
							// no-op
						}

					};
					try {
						coinfloor.cancelAllOrdersAsync(callback);
					}
					catch (IOException e) {
						callback.failed(e);
					}
				}

			});

			cancelSelectedButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					cancelSelected(bidsTable);
					cancelSelected(asksTable);
				}

				private void cancelSelected(OrdersTable table) {
					OrdersTableModel model = table.getModel();
					SwingCallback<OrderInfo> callback = new SwingCallback<OrderInfo>(MyOrdersPanel.this, "Failed to cancel order.") {

						@Override
						void completed(OrderInfo info) {
							// no-op
						}

					};
					try {
						for (int i = 0, n = table.getRowCount(); i < n; ++i) {
							if (table.isRowSelected(i)) {
								Order order = model.orders.get(table.convertRowIndexToModel(i));
								if (order.remaining > 0) {
									coinfloor.cancelOrderAsync(order.id, callback);
								}
								table.removeRowSelectionInterval(i, i);
							}
						}
					}
					catch (IOException e) {
						callback.failed(e);
					}
				}

			});

			clearClosedButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					clearClosed(bidsTable);
					clearClosed(asksTable);
					clearClosedButton.setEnabled(false);
				}

				private void clearClosed(OrdersTable table) {
					OrdersTableModel model = table.getModel();
					for (int i = 0, n = table.getRowCount(); i < n;) {
						if (model.orders.get(i).remaining == 0) {
							model.orders.remove(i);
							--n;
						}
						else {
							++i;
						}
					}
					model.fireTableDataChanged();
				}

			});

			GroupLayout layout = (GroupLayout) getLayout();
			// @formatter:off
			layout.setHorizontalGroup(horizontalGroup = layout.createParallelGroup(Alignment.CENTER)
					.addGroup(horizontalGroup)
					.addGroup(layout.createSequentialGroup()
							.addComponent(cancelAllButton)
							.addPreferredGap(ComponentPlacement.UNRELATED)
							.addComponent(cancelSelectedButton)
							.addPreferredGap(ComponentPlacement.UNRELATED)
							.addComponent(clearClosedButton)));
			layout.setVerticalGroup(verticalGroup = layout.createSequentialGroup()
					.addGroup(verticalGroup)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(cancelAllButton)
							.addComponent(cancelSelectedButton)
							.addComponent(clearClosedButton)));
			// @formatter:on
		}

		@Override
		public void orderOpened(long id, boolean own, AssetType base, AssetType counter, long quantity, long price) {
			if (own) {
				super.orderOpened(id, own, base, counter, quantity, price);
			}
		}

		@Override
		public void orderMatched(long id, boolean own, AssetType base, AssetType counter, long quantity, long remaining) {
			if (own) {
				super.orderMatched(id, own, base, counter, quantity, remaining);
			}
		}

		@Override
		public void orderClosed(long id, boolean own, AssetType base, AssetType counter, long quantity, long price) {
			if (own) {
				super.orderClosed(id, own, base, counter, quantity, price);
			}
		}

		@Override
		void addOrder(long id, long price, long quantity, long filled, long remaining) {
			super.addOrder(id, price, quantity, filled, remaining);
			cancelAllButton.setEnabled((totalRemaining += Math.abs(remaining)) > 0);
		}

		@Override
		Long updateOrder(long id, long filledDelta, long remaining) {
			Long ret;
			if ((ret = super.updateOrder(id, filledDelta, remaining)) != null) {
				cancelAllButton.setEnabled((totalRemaining += Math.abs(remaining) - ret) > 0);
				if (remaining == 0) {
					clearClosedButton.setEnabled(true);
				}
			}
			return ret;
		}

		@Override
		Long removeOrder(long id) {
			return updateOrder(id, 0, 0);
		}

		@Override
		void removeAllOrders() {
			super.removeAllOrders();
			totalRemaining = 0;
			cancelAllButton.setEnabled(false);
		}

		@Override
		void subscribe(final AssetType.Pair market) {
			SwingCallback<Map<Long, OrderInfo>> callback = new SwingCallback<Map<Long, OrderInfo>>(this, "Failed to retrieve orders.") {

				@Override
				void completed(Map<Long, OrderInfo> orders) {
					removeAllOrders();
					for (Map.Entry<Long, OrderInfo> entry : orders.entrySet()) {
						OrderInfo info = entry.getValue();
						if (info.base == market.base.code && info.counter == market.counter.code) {
							addOrder(entry.getKey(), info.price, info.quantity, 0, info.quantity);
						}
					}
				}

			};
			try {
				coinfloor.getOrdersAsync(callback);
			}
			catch (IOException e) {
				callback.failed(e);
			}
		}

		@Override
		void unsubscribe(AssetType.Pair market) {
			removeAllOrders();
		}

	}

	class OrdersTable extends JTable {

		OrdersTable() {
			super(new OrdersTableModel());
			setAutoCreateRowSorter(true);
			TableColumnModel columnModel = getColumnModel();
			columnModel.getColumn(0).setCellRenderer(new PriceRenderer());
			columnModel.getColumn(1).setCellRenderer(new QuantityRenderer());
			columnModel.getColumn(2).setCellRenderer(new QuantityRenderer());
			columnModel.getColumn(3).setCellRenderer(new QuantityRenderer());
		}

		@Override
		public OrdersTableModel getModel() {
			return (OrdersTableModel) super.getModel();
		}

	}

	static class OrdersTableModel extends AbstractTableModel {

		final ArrayList<Order> orders = new ArrayList<Order>();

		@Override
		public int getRowCount() {
			return orders.size();
		}

		@Override
		public int getColumnCount() {
			return 4;
		}

		@Override
		public String getColumnName(int column) {
			switch (column) {
				case 0:
					return "Price";
				case 1:
					return "Quantity";
				case 2:
					return "Filled";
				case 3:
					return "Remaining";
			}
			throw new IndexOutOfBoundsException();
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return Long.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			Order order = orders.get(rowIndex);
			switch (columnIndex) {
				case 0:
					return order.price;
				case 1:
					return order.quantity;
				case 2:
					return order.filled;
				case 3:
					return order.remaining;
			}
			throw new IndexOutOfBoundsException();
		}

	}

	class PriceRenderer extends DefaultTableCellRenderer {

		PriceRenderer() {
			setHorizontalAlignment(SwingConstants.TRAILING);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Component c = super.getTableCellRendererComponent(table, market.counter.format(BigDecimal.valueOf(((Number) value).longValue(), market.counter.scale - market.base.scale + 4)), isSelected, hasFocus, row, column);
			c.setEnabled(((OrdersTableModel) table.getModel()).orders.get(table.convertRowIndexToModel(row)).remaining > 0);
			return c;
		}

	}

	class QuantityRenderer extends DefaultTableCellRenderer {

		QuantityRenderer() {
			setHorizontalAlignment(SwingConstants.TRAILING);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			long v = ((Number) value).longValue();
			Component c = super.getTableCellRendererComponent(table, v == 0 ? "\u2014" : market.base.format(BigDecimal.valueOf(v, market.base.scale)), isSelected, hasFocus, row, column);
			c.setEnabled(((OrdersTableModel) table.getModel()).orders.get(table.convertRowIndexToModel(row)).remaining > 0);
			return c;
		}

	}

	static class Order {

		final long id, price, quantity;

		long filled, remaining;

		Order(long id, long price, long quantity, long filled, long remaining) {
			this.id = id;
			this.price = price;
			this.filled = filled;
			this.remaining = remaining;
			remaining = this.quantity = quantity;
		}

	}

	final ExecutorService executor = Executors.newSingleThreadExecutor();
	final Coinfloor coinfloor = new Coinfloor() {

		@Override
		protected void balanceChanged(int asset, final long balance) {
			final AssetType assetType;
			if ((assetType = AssetType.forCode(asset)) != null) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						fireBalanceChanged(assetType, balance);
					}

				});
			}
		}

		@Override
		protected void orderOpened(final long id, long tonce, int base, int counter, final long quantity, final long price, long time, final boolean own) {
			final AssetType baseAssetType, counterAssetType;
			if ((baseAssetType = AssetType.forCode(base)) != null && (counterAssetType = AssetType.forCode(counter)) != null) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						fireOrderOpened(id, own, baseAssetType, counterAssetType, quantity, price);
					}

				});
			}
		}

		@Override
		protected void ordersMatched(final long bid, long bidTonce, final long ask, long askTonce, int base, int counter, final long quantity, long price, long total, final long bidRem, final long askRem, long time, final long bidBaseFee, long bidCounterFee, final long askBaseFee, long askCounterFee) {
			final AssetType baseAssetType, counterAssetType;
			if ((baseAssetType = AssetType.forCode(base)) != null && (counterAssetType = AssetType.forCode(counter)) != null) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						fireOrderMatched(bid, bidBaseFee >= 0, baseAssetType, counterAssetType, quantity, bidRem);
						fireOrderMatched(ask, askBaseFee >= 0, baseAssetType, counterAssetType, quantity, -askRem);
					}

				});
			}
		}

		@Override
		protected void orderClosed(final long id, long tonce, int base, int counter, final long quantity, final long price, final boolean own) {
			final AssetType baseAssetType, counterAssetType;
			if ((baseAssetType = AssetType.forCode(base)) != null && (counterAssetType = AssetType.forCode(counter)) != null) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						fireOrderClosed(id, own, baseAssetType, counterAssetType, quantity, price);
					}

				});
			}
		}

		@Override
		protected void tickerChanged(int base, int counter, final long last, final long bid, final long ask, final long low, final long high, final long volume) {
			final AssetType baseAssetType, counterAssetType;
			if ((baseAssetType = AssetType.forCode(base)) != null && (counterAssetType = AssetType.forCode(counter)) != null) {
				SwingUtilities.invokeLater(new Runnable() {

					@Override
					public void run() {
						fireTickerChanged(baseAssetType, counterAssetType, last, bid, ask, low, high, volume);
					}

				});
			}
		}

		@Override
		protected void disconnected(final IOException e) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					JOptionPane.showMessageDialog(SwingClient.this, "Lost connection to Coinfloor.\n\n" + e, "Connection Lost", JOptionPane.ERROR_MESSAGE);
				}

			});
		}

	};

	final CardLayout cardLayout;
	final AuthPanel authPanel;
	final JPanel mainPanel;

	final TickerPanel tickerPanel;
	final TradePanel tradePanel;
	final BalancesPanel balancesPanel;

	AssetType.Pair market;
	boolean connected, authenticated;

	public SwingClient() {
		super(null);
		setLayout(cardLayout = new CardLayout());
		add(authPanel = new AuthPanel(), authPanel.getClass().getSimpleName());
		tickerPanel = new TickerPanel();
		tradePanel = new TradePanel();
		balancesPanel = new BalancesPanel();
		add(mainPanel = new JPanel(null), "Main");

		GroupLayout layout = new GroupLayout(mainPanel);
		layout.setAutoCreateGaps(true);
		// @formatter:off
		layout.setHorizontalGroup(layout.createParallelGroup()
				.addComponent(tickerPanel)
				.addComponent(tradePanel)
				.addComponent(balancesPanel));
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addComponent(tickerPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
				.addComponent(tradePanel)
				.addComponent(balancesPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE));
		// @formatter:on
		mainPanel.setLayout(layout);
	}

	public final void addBalanceListener(BalanceListener balanceListener) {
		listenerList.add(BalanceListener.class, balanceListener);
	}

	public final void removeBalanceListener(BalanceListener balanceListener) {
		listenerList.remove(BalanceListener.class, balanceListener);
	}

	public final BalanceListener[] getBalanceListeners() {
		return listenerList.getListeners(BalanceListener.class);
	}

	protected final void fireBalanceChanged(AssetType assetType, long balance) {
		BalanceListener[] balanceListeners = getBalanceListeners();
		if (balanceListeners.length > 0) {
			for (BalanceListener balanceListener : balanceListeners) {
				balanceListener.balanceChanged(assetType, balance);
			}
		}
	}

	public final void addOrderListener(OrderListener orderListener) {
		listenerList.add(OrderListener.class, orderListener);
	}

	public final void removeOrderListener(OrderListener orderListener) {
		listenerList.remove(OrderListener.class, orderListener);
	}

	public final OrderListener[] getOrderListeners() {
		return listenerList.getListeners(OrderListener.class);
	}

	protected final void fireOrderOpened(long id, boolean own, AssetType base, AssetType counter, long quantity, long price) {
		OrderListener[] orderListeners = getOrderListeners();
		if (orderListeners.length > 0) {
			for (OrderListener orderListener : orderListeners) {
				orderListener.orderOpened(id, own, base, counter, quantity, price);
			}
		}
	}

	protected final void fireOrderMatched(long id, boolean own, AssetType base, AssetType counter, long quantity, long remaining) {
		OrderListener[] orderListeners = getOrderListeners();
		if (orderListeners.length > 0) {
			for (OrderListener orderListener : orderListeners) {
				orderListener.orderMatched(id, own, base, counter, quantity, remaining);
			}
		}
	}

	protected final void fireOrderClosed(long id, boolean own, AssetType base, AssetType counter, long quantity, long price) {
		OrderListener[] orderListeners = getOrderListeners();
		if (orderListeners.length > 0) {
			for (OrderListener orderListener : orderListeners) {
				orderListener.orderClosed(id, own, base, counter, quantity, price);
			}
		}
	}

	public final void addTickerListener(TickerListener tickerListener) {
		listenerList.add(TickerListener.class, tickerListener);
	}

	public final void removeTickerListener(TickerListener tickerListener) {
		listenerList.remove(TickerListener.class, tickerListener);
	}

	public final TickerListener[] getTickerListeners() {
		return listenerList.getListeners(TickerListener.class);
	}

	protected final void fireTickerChanged(AssetType base, AssetType counter, long last, long bid, long ask, long low, long high, long volume) {
		TickerListener[] tickerListeners = getTickerListeners();
		if (tickerListeners.length > 0) {
			for (TickerListener tickerListener : tickerListeners) {
				tickerListener.tickerChanged(base, counter, last, bid, ask, low, high, volume);
			}
		}
	}

	void setMarket(AssetType.Pair market) {
		assert SwingUtilities.isEventDispatchThread();
		firePropertyChange("market", this.market, this.market = market);
	}

	void setConnected(boolean connected) {
		assert SwingUtilities.isEventDispatchThread();
		firePropertyChange("connected", this.connected, this.connected = connected);
	}

	void setAuthenticated(boolean authenticated) {
		assert SwingUtilities.isEventDispatchThread();
		firePropertyChange("authenticated", this.authenticated, this.authenticated = authenticated);
	}

	public static void main(String[] args) throws Exception {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				}
				catch (Exception ignored) {
				}
				JFrame frame = new JFrame("Coinfloor Trader");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setLocationByPlatform(true);
				frame.add(new SwingClient());
				frame.pack();
				frame.setVisible(true);
			}

		});
	}

	static BigDecimal getAmount(JTextComponent component) {
		try {
			String str = component.getText();
			return str.isEmpty() ? null : new BigDecimal(str);
		}
		catch (Exception e) {
			return null;
		}
	}

}

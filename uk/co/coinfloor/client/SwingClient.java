/*
 * Created on Mar 8, 2014
 */
package uk.co.coinfloor.client;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;

import uk.co.coinfloor.api.Callback;
import uk.co.coinfloor.api.Coinfloor;
import uk.co.coinfloor.api.Coinfloor.MarketOrderEstimate;
import uk.co.coinfloor.api.Coinfloor.OrderInfo;
import uk.co.coinfloor.api.Coinfloor.TickerInfo;

@SuppressWarnings("serial")
public class SwingClient extends JPanel {

	static abstract class SwingCallback<V> implements Callback<V> {

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

		abstract void completed(V result);

		void failed(Exception exception) {
			JOptionPane.showMessageDialog(parent, errorPreamble == null ? exception.toString() : errorPreamble + "\n\n" + exception, "Error", JOptionPane.WARNING_MESSAGE);
		}

	}

	class TickerPanel extends JPanel {

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

		void update(long last, long bid, long ask, long low, long high, long volume) {
			lastLabel.setText(last < 0 ? "\u2014" : counter.symbol + BigDecimal.valueOf(last, counter.scale));
			bidLabel.setText(bid < 0 ? "\u2014" : counter.symbol + BigDecimal.valueOf(bid, counter.scale));
			askLabel.setText(ask < 0 ? "\u2014" : counter.symbol + BigDecimal.valueOf(ask, counter.scale));
			lowLabel.setText(low < 0 ? "\u2014" : counter.symbol + BigDecimal.valueOf(low, counter.scale));
			highLabel.setText(high < 0 ? "\u2014" : counter.symbol + BigDecimal.valueOf(high, counter.scale));
			volumeLabel.setText(volume < 0 ? "\u2014" : base.symbol + BigDecimal.valueOf(volume, base.scale));
		}

	}

	class BalancesPanel extends JPanel {

		final JLabel baseBalanceLabel, counterBalanceLabel;

		BalancesPanel() {
			super(new GridLayout(1, 2, 10, 0));
			setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 20, 10, 20)));
			add(counterBalanceLabel = new JLabel("\u2014", SwingConstants.CENTER));
			add(baseBalanceLabel = new JLabel("\u2014", SwingConstants.CENTER));
		}

		void update(AssetType assetType, long balance) {
			JLabel label;
			if (assetType == base) {
				label = baseBalanceLabel;
			}
			else if (assetType == counter) {
				label = counterBalanceLabel;
			}
			else {
				return;
			}
			label.setText(assetType.symbol + BigDecimal.valueOf(balance, assetType.scale));
		}

	}

	class AuthPanel extends JPanel {

		final JTextField userIDField;
		final JPasswordField cookieField, passphraseField;
		final JButton authenticateButton;

		AuthPanel() {
			super(null);

			JLabel userIDLabel = new JLabel("User ID:");
			userIDLabel.setLabelFor(userIDField = new JTextField(10));

			JLabel cookieLabel = new JLabel("Cookie:");
			cookieLabel.setLabelFor(cookieField = new JPasswordField(20));

			JLabel passphraseLabel = new JLabel("Passphrase:");
			passphraseLabel.setLabelFor(passphraseField = new JPasswordField(20));

			authenticateButton = new JButton("Authenticate");
			authenticateButton.setEnabled(false);

			authenticateButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent event) {
					long userID;
					try {
						userID = Long.parseLong(userIDField.getText());
					}
					catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(AuthPanel.this, "Invalid user ID.", "Error", JOptionPane.WARNING_MESSAGE);
						userIDField.requestFocusInWindow();
						return;
					}
					String cookie = new String(cookieField.getPassword());
					if (cookie.isEmpty()) {
						JOptionPane.showMessageDialog(AuthPanel.this, "Cookie is required.", "Error", JOptionPane.WARNING_MESSAGE);
						cookieField.requestFocusInWindow();
						return;
					}
					String passphrase = new String(passphraseField.getPassword());
					authenticateButton.setEnabled(false);
					try {
						coinfloor.authenticateAsync(userID, cookie, passphrase, new SwingCallback<Void>(AuthPanel.this, "Authentication failed.") {

							@Override
							void completed(Void result) {
								cardLayout.next(cardPanel);
								try {
									coinfloor.getBalancesAsync(new SwingCallback<Map<Integer, Long>>(AuthPanel.this, "Failed to retrieve balances.") {

										@Override
										void completed(Map<Integer, Long> balances) {
											for (Map.Entry<Integer, Long> entry : balances.entrySet()) {
												balancesPanel.update(AssetType.forCode(entry.getKey()), entry.getValue());
											}
										}

									});
								}
								catch (IOException e) {
									JOptionPane.showMessageDialog(AuthPanel.this, "Failed to retrieve balances.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
								}
								try {
									coinfloor.getOrdersAsync(new SwingCallback<Map<Long, OrderInfo>>(SwingClient.this, "Failed to retrieve orders.") {

										@Override
										void completed(Map<Long, OrderInfo> orders) {
											for (Map.Entry<Long, OrderInfo> entry : orders.entrySet()) {
												OrderInfo info = entry.getValue();
												tradePanel.myOrdersPanel.addOrder(entry.getKey(), info.price, info.quantity, 0, info.quantity);
											}
										}

									});
								}
								catch (IOException e) {
									JOptionPane.showMessageDialog(AuthPanel.this, "Failed to retrieve orders.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
								}
							}

							@Override
							void failed(Exception exception) {
								authenticateButton.setEnabled(true);
								super.failed(exception);
							}

						});
					}
					catch (IOException e) {
						JOptionPane.showMessageDialog(AuthPanel.this, "Failed to send authentication.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
					}
				}

			});

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addGroup(layout.createParallelGroup(Alignment.TRAILING)
							.addComponent(userIDLabel)
							.addComponent(cookieLabel)
							.addComponent(passphraseLabel))
					.addGroup(layout.createParallelGroup()
							.addComponent(userIDField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(cookieField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(passphraseField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(authenticateButton))
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			layout.setVerticalGroup(layout.createSequentialGroup()
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
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
					.addComponent(authenticateButton)
					.addContainerGap(GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE));
			// @formatter:on
			setLayout(layout);
		}

	}

	class TradePanel extends JPanel {

		final OrderPanel orderPanel = new OrderPanel();
		final MyOrdersPanel myOrdersPanel = new MyOrdersPanel();
		final OrdersPanel ordersPanel = new OrdersPanel();

		TradePanel() {
			super(null);

			JSeparator separator = new JSeparator();

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateContainerGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(layout.createParallelGroup()
					.addComponent(orderPanel)
					.addComponent(myOrdersPanel)
					.addComponent(separator)
					.addComponent(ordersPanel));
			layout.setVerticalGroup(layout.createSequentialGroup()
					.addComponent(orderPanel)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(myOrdersPanel)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(separator)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(ordersPanel));
			// @formatter:on
			setLayout(layout);
		}

	}

	class OrderPanel extends JPanel {

		final MarketOrderPanel marketOrderPanel;
		final LimitOrderPanel limitOrderPanel;

		OrderPanel() {
			super(null);

			JTabbedPane tabbedPane = new JTabbedPane();
			tabbedPane.addTab("Market Order", marketOrderPanel = new MarketOrderPanel());
			tabbedPane.addTab("Limit Order", limitOrderPanel = new LimitOrderPanel());

			GroupLayout layout = new GroupLayout(this);
			layout.setHorizontalGroup(layout.createSequentialGroup().addComponent(tabbedPane));
			layout.setVerticalGroup(layout.createSequentialGroup().addComponent(tabbedPane));
			setLayout(layout);
		}

	}

	class MarketOrderPanel extends JPanel {

		final Timer timer = new Timer(true);

		final JRadioButton buyRadioButton, sellRadioButton, spendRadioButton, getRadioButton;
		final JTextField amountField;
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

			final JLabel amountLabel = new JLabel("Quantity:");
			final JLabel amountSymbolLabel = new JLabel(base.symbol);
			amountLabel.setLabelFor(amountField = new JTextField(10));

			JLabel estimatedQuantityLabelLabel = new JLabel("Estimated Quantity:");
			estimatedQuantityLabelLabel.setLabelFor(estimatedQuantityLabel = new JLabel("\u2014"));
			JLabel estimatedTotalLabelLabel = new JLabel("Estimated Total:");
			estimatedTotalLabelLabel.setLabelFor(estimatedTotalLabel = new JLabel("\u2014"));
			JLabel averagePriceLabelLabel = new JLabel("Average Price:");
			averagePriceLabelLabel.setLabelFor(averagePriceLabel = new JLabel("\u2014"));

			executeButton = new JButton("Execute");
			executeButton.setEnabled(false);

			ActionListener radioButtonListener = new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent e) {
					if (buyRadioButton.isSelected() || sellRadioButton.isSelected()) {
						amountLabel.setText("Quantity:");
						amountSymbolLabel.setText(base.symbol);
					}
					else if (spendRadioButton.isSelected() || getRadioButton.isSelected()) {
						amountLabel.setText("Total:");
						amountSymbolLabel.setText(counter.symbol);
					}
					inputChanged();
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
					BigDecimal amount = new BigDecimal(amountField.getText());
					SwingCallback<Long> callback = new SwingCallback<Long>(MarketOrderPanel.this, "Failed to execute market order.") {

						@Override
						void completed(Long remaining) {
							JOptionPane.showMessageDialog(MarketOrderPanel.this, "Your market order was executed.", "Market Order", JOptionPane.INFORMATION_MESSAGE);
						}

					};
					executeButton.setEnabled(false);
					try {
						if (buyRadioButton.isSelected()) {
							coinfloor.executeBaseMarketOrderAsync(base.code, counter.code, amount.movePointRight(base.scale).longValue(), callback);
						}
						else if (sellRadioButton.isSelected()) {
							coinfloor.executeBaseMarketOrderAsync(base.code, counter.code, -amount.movePointRight(base.scale).longValue(), callback);
						}
						else if (spendRadioButton.isSelected()) {
							coinfloor.executeCounterMarketOrderAsync(base.code, counter.code, amount.movePointRight(counter.scale).longValue(), callback);
						}
						else if (getRadioButton.isSelected()) {
							coinfloor.executeCounterMarketOrderAsync(base.code, counter.code, -amount.movePointRight(counter.scale).longValue(), callback);
						}
					}
					catch (IOException e) {
						JOptionPane.showMessageDialog(MarketOrderPanel.this, "Failed to execute market order.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
					}
				}

			});

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateContainerGaps(true);
			layout.setAutoCreateGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addGroup(layout.createParallelGroup(Alignment.TRAILING)
							.addGroup(layout.createSequentialGroup()
									.addComponent(buyRadioButton)
									.addComponent(sellRadioButton)
									.addComponent(spendRadioButton)
									.addComponent(getRadioButton))
							.addGroup(layout.createSequentialGroup()
									.addComponent(amountLabel)
									.addComponent(amountSymbolLabel)
									.addGap(0)
									.addComponent(amountField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
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
					.addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addComponent(executeButton));
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
									.addComponent(amountField)))
					.addGroup(layout.createSequentialGroup()
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(estimatedQuantityLabelLabel)
									.addComponent(estimatedQuantityLabel))
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(estimatedTotalLabelLabel)
									.addComponent(estimatedTotalLabel))
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(averagePriceLabelLabel)
									.addComponent(averagePriceLabel)))
					.addComponent(executeButton));
			// @formatter:on
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
			String amountStr = amountField.getText();
			BigDecimal amountBig;
			try {
				if (amountStr.isEmpty() || (amountBig = new BigDecimal(amountStr)).signum() < 0) {
					return;
				}
			}
			catch (Exception e) {
				return;
			}
			final boolean useBase;
			final long amount;
			if (buyRadioButton.isSelected()) {
				useBase = true;
				amount = amountBig.movePointRight(base.scale).longValue();
			}
			else if (sellRadioButton.isSelected()) {
				useBase = true;
				amount = -amountBig.movePointRight(base.scale).longValue();
			}
			else if (spendRadioButton.isSelected()) {
				useBase = false;
				amount = amountBig.movePointRight(counter.scale).longValue();
			}
			else if (getRadioButton.isSelected()) {
				useBase = false;
				amount = -amountBig.movePointRight(counter.scale).longValue();
			}
			else {
				return;
			}
			executeButton.setEnabled(true);
			timer.schedule(timerTask = new TimerTask() {

				@Override
				public void run() {
					Callback<MarketOrderEstimate> callback = new SwingCallback<MarketOrderEstimate>(MarketOrderPanel.this, null) {

						@Override
						void completed(MarketOrderEstimate estimate) {
							BigDecimal estimatedQuantity = BigDecimal.valueOf(estimate.quantity, base.scale);
							estimatedQuantityLabel.setText(base.symbol + estimatedQuantity);
							BigDecimal estimatedTotal = BigDecimal.valueOf(estimate.total, counter.scale);
							estimatedTotalLabel.setText(counter.symbol + estimatedTotal);
							BigDecimal averagePrice = estimatedTotal.divide(estimatedQuantity, counter.scale, RoundingMode.HALF_EVEN);
							averagePriceLabel.setText(counter.symbol + averagePrice);
						}

						@Override
						void failed(Exception exception) {
							// no-op
						}

					};
					try {
						if (useBase) {
							coinfloor.estimateBaseMarketOrderAsync(base.code, counter.code, amount, callback);
						}
						else {
							coinfloor.estimateCounterMarketOrderAsync(base.code, counter.code, amount, callback);
						}
					}
					catch (IOException ignored) {
					}
				}

			}, 1000, 5000);
		}

	}

	class LimitOrderPanel extends JPanel {

		final JRadioButton bidRadioButton, askRadioButton;
		final JTextField priceField, quantityField;
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

			JLabel priceLabel = new JLabel("Price:");
			JLabel priceSymbolLabel = new JLabel(counter.symbol);
			priceLabel.setLabelFor(priceField = new JTextField(10));

			JLabel quantityLabel = new JLabel("Quantity:");
			JLabel quantitySymbolLabel = new JLabel(base.symbol);
			quantityLabel.setLabelFor(quantityField = new JTextField(10));

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
					long quantity = new BigDecimal(quantityField.getText()).movePointRight(base.scale).longValue();
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
					final long price = new BigDecimal(priceField.getText()).movePointRight(counter.scale).longValue();
					SwingCallback<Long> callback = new SwingCallback<Long>(LimitOrderPanel.this, "Failed to submit limit order.") {

						@Override
						void completed(Long orderID) {
							tradePanel.myOrdersPanel.addOrder(orderID, price, fQuantity, 0, fQuantity);
						}

					};
					submitButton.setEnabled(false);
					try {
						coinfloor.placeLimitOrderAsync(base.code, counter.code, fQuantity, price, callback);
					}
					catch (IOException e) {
						JOptionPane.showMessageDialog(LimitOrderPanel.this, "Failed to submit limit order.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
					}
				}

			});

			GroupLayout layout = new GroupLayout(this);
			layout.setAutoCreateContainerGaps(true);
			layout.setAutoCreateGaps(true);
			// @formatter:off
			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addComponent(priceLabel)
					.addComponent(priceSymbolLabel)
					.addGap(0)
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
							.addGroup(layout.createSequentialGroup()
									.addComponent(bidRadioButton)
									.addComponent(askRadioButton))
							.addComponent(priceField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
					.addComponent(quantityLabel)
					.addComponent(quantitySymbolLabel)
					.addGap(0)
					.addComponent(quantityField, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
					.addComponent(submitButton));
			layout.setVerticalGroup(layout.createParallelGroup(Alignment.CENTER)
					.addGroup(layout.createSequentialGroup()
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(bidRadioButton)
									.addComponent(askRadioButton))
							.addGroup(layout.createBaselineGroup(false, false)
									.addComponent(priceLabel)
									.addComponent(priceSymbolLabel)
									.addComponent(priceField)
									.addComponent(quantityLabel)
									.addComponent(quantitySymbolLabel)
									.addComponent(quantityField)))
					.addComponent(submitButton));
			// @formatter:on
			setLayout(layout);
		}

		void inputChanged() {
			submitButton.setEnabled(false);
			String quantityStr = quantityField.getText(), priceStr = priceField.getText();
			try {
				if (quantityStr.isEmpty() || new BigDecimal(quantityStr).signum() < 0) {
					return;
				}
				if (priceStr.isEmpty() || new BigDecimal(priceStr).signum() < 0) {
					return;
				}
			}
			catch (Exception e) {
				return;
			}
			submitButton.setEnabled(true);
		}

	}

	class OrdersPanel extends JPanel {

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
							.addComponent(bidsTableScrollPane, GroupLayout.DEFAULT_SIZE, 300, GroupLayout.DEFAULT_SIZE))
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createParallelGroup(Alignment.CENTER)
							.addComponent(asksLabel)
							.addComponent(asksTableScrollPane, GroupLayout.DEFAULT_SIZE, 300, GroupLayout.DEFAULT_SIZE)));
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
			OrdersTableModel model = (OrdersTableModel) table.getModel();
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

	}

	class MyOrdersPanel extends OrdersPanel {

		final JButton cancelAllButton, cancelSelectedButton;

		final HashMap<Long, long[]> cachedUpdates = new HashMap<Long, long[]>();

		long totalRemaining;

		MyOrdersPanel() {
			super("My Bids", "My Asks");

			bidsTable.setRowSelectionAllowed(true);
			asksTable.setRowSelectionAllowed(true);

			cancelAllButton = new JButton("Cancel All Orders");
			cancelAllButton.setEnabled(false);

			cancelSelectedButton = new JButton("Cancel Selected Orders");
			cancelSelectedButton.setEnabled(false);

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
				public void actionPerformed(ActionEvent e) {
					cancelAll(bidsTable);
					cancelAll(asksTable);
				}

				private void cancelAll(OrdersTable table) {
					OrdersTableModel model = (OrdersTableModel) table.getModel();
					SwingCallback<OrderInfo> callback = new SwingCallback<OrderInfo>(MyOrdersPanel.this, "Failed to cancel order.") {

						@Override
						void completed(OrderInfo info) {
							// no-op
						}

					};
					try {
						for (Order order : model.orders) {
							if (order.remaining > 0) {
								coinfloor.cancelOrderAsync(order.id, callback);
							}
						}
					}
					catch (IOException e) {
						JOptionPane.showMessageDialog(MyOrdersPanel.this, "Failed to cancel order.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
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
					OrdersTableModel model = (OrdersTableModel) table.getModel();
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
						JOptionPane.showMessageDialog(MyOrdersPanel.this, "Failed to cancel order.\n\n" + e, "Error", JOptionPane.WARNING_MESSAGE);
					}
				}

			});

			GroupLayout layout = (GroupLayout) getLayout();
			// @formatter:off
			layout.setHorizontalGroup(horizontalGroup = layout.createParallelGroup()
					.addGroup(horizontalGroup)
					.addGroup(layout.createSequentialGroup()
							.addComponent(cancelAllButton)
							.addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.DEFAULT_SIZE, Integer.MAX_VALUE)
							.addComponent(cancelSelectedButton)));
			layout.setVerticalGroup(verticalGroup = layout.createSequentialGroup()
					.addGroup(verticalGroup)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addGroup(layout.createBaselineGroup(false, false)
							.addComponent(cancelAllButton)
							.addComponent(cancelSelectedButton)));
			// @formatter:on
		}

		@Override
		void addOrder(long id, long price, long quantity, long filled, long remaining) {
			long[] cachedUpdate = cachedUpdates.remove(id);
			if (cachedUpdate != null) {
				filled = cachedUpdate[0];
				remaining = cachedUpdate[1];
			}
			super.addOrder(id, price, quantity, filled, remaining);
			cancelAllButton.setEnabled((totalRemaining += Math.abs(remaining)) > 0);
		}

		@Override
		Long updateOrder(long id, long filledDelta, long remaining) {
			Long ret;
			if ((ret = super.updateOrder(id, filledDelta, remaining)) != null) {
				cancelAllButton.setEnabled((totalRemaining += Math.abs(remaining) - ret) > 0);
				return ret;
			}
			long[] cachedUpdate = cachedUpdates.get(id);
			if (cachedUpdate == null) {
				cachedUpdates.put(id, new long[] { filledDelta, remaining });
			}
			else {
				cachedUpdate[0] += filledDelta;
				cachedUpdate[1] = remaining;
			}
			return null;
		}

		@Override
		Long removeOrder(long id) {
			return updateOrder(id, 0, 0);
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
			Component c = super.getTableCellRendererComponent(table, counter.symbol + BigDecimal.valueOf(((Number) value).longValue(), counter.scale - base.scale + 4), isSelected, hasFocus, row, column);
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
			Component c = super.getTableCellRendererComponent(table, v == 0 ? "\u2014" : base.symbol + BigDecimal.valueOf(v, base.scale), isSelected, hasFocus, row, column);
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

	final Coinfloor coinfloor = new Coinfloor() {

		@Override
		protected void balanceChanged(final int asset, final long balance) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					balancesPanel.update(AssetType.forCode(asset), balance);
				}

			});
		}

		@Override
		protected void orderOpened(final long id, int base, int counter, final long quantity, final long price, long time) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					tradePanel.ordersPanel.addOrder(id, price, quantity, 0, quantity);
				}

			});
		}

		@Override
		protected void ordersMatched(final long bid, final long ask, int base, int counter, final long quantity, long price, long total, final long bidRem, final long askRem, long time, final long bidBaseFee, long bidCounterFee, final long askBaseFee, long askCounterFee) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					if (bidBaseFee >= 0) {
						tradePanel.myOrdersPanel.updateOrder(bid, quantity, bidRem);
					}
					if (askBaseFee >= 0) {
						tradePanel.myOrdersPanel.updateOrder(ask, quantity, -askRem);
					}
					tradePanel.ordersPanel.updateOrder(bid, quantity, bidRem);
					tradePanel.ordersPanel.updateOrder(ask, quantity, -askRem);
				}

			});
		}

		@Override
		protected void orderClosed(final long id, int base, int counter, long quantity, long price) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					tradePanel.myOrdersPanel.removeOrder(id);
					tradePanel.ordersPanel.removeOrder(id);
				}

			});
		}

		@Override
		protected void tickerChanged(int base, int counter, final long last, final long bid, final long ask, final long low, final long high, final long volume) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					tickerPanel.update(last, bid, ask, low, high, volume);
				}

			});
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

	final TickerPanel tickerPanel;
	final BalancesPanel balancesPanel;

	final JPanel cardPanel;
	final CardLayout cardLayout;
	final AuthPanel authPanel;
	final TradePanel tradePanel;

	AssetType base = AssetType.XBT, counter = AssetType.GBP;

	public SwingClient() {
		super(new BorderLayout());
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		add(tickerPanel = new TickerPanel(), BorderLayout.PAGE_START);
		add(balancesPanel = new BalancesPanel(), BorderLayout.PAGE_END);
		add(cardPanel = new JPanel(cardLayout = new CardLayout()), BorderLayout.CENTER);
		cardPanel.add(authPanel = new AuthPanel(), authPanel.getClass().getSimpleName());
		cardPanel.add(tradePanel = new TradePanel(), tradePanel.getClass().getSimpleName());
		new Thread() {

			@Override
			public void run() {
				try {
					coinfloor.connect(URI.create("ws://api.coinfloor.co.uk/"));
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							setCursor(null);
							authPanel.authenticateButton.setEnabled(true);
						}

					});
					coinfloor.watchTickerAsync(base.code, counter.code, true, new SwingCallback<TickerInfo>(SwingClient.this, "Failed to subscribe to ticker.") {

						@Override
						void completed(TickerInfo ticker) {
							tickerPanel.update(ticker.last, ticker.bid, ticker.ask, ticker.low, ticker.high, ticker.volume);
						}

					});
					coinfloor.watchOrdersAsync(base.code, counter.code, true, new SwingCallback<Map<Long, OrderInfo>>(SwingClient.this, "Failed to subscribe to orders.") {

						@Override
						void completed(Map<Long, OrderInfo> orders) {
							for (Map.Entry<Long, OrderInfo> entry : orders.entrySet()) {
								OrderInfo info = entry.getValue();
								tradePanel.ordersPanel.addOrder(entry.getKey(), info.price, info.quantity, 0, info.quantity);
							}
						}

					});
				}
				catch (final IOException e) {
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							setCursor(null);
							JOptionPane.showMessageDialog(SwingClient.this, "Failed to connect to Coinfloor server.\n\n" + e, "Connection Failed", JOptionPane.ERROR_MESSAGE);
						}

					});
				}
			}

		}.start();
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
				JFrame frame = new JFrame("Coinfloor");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				frame.setLocationByPlatform(true);
				frame.add(new SwingClient());
				frame.pack();
				frame.setVisible(true);
			}

		});
	}

}

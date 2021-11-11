# HomeGenie

Home automation tools

This project uses a combination of web scraping and IoT platform IFTTT to save energy by switching off devices that are idle. 

The scraper scrapes the home router's portal at periodic intervals to detect the incoming and outgoing data rate of a particular device. In the sample case my Samsung TV which is connected to my D-Link router is detected idle if its incoming and outgoing data rates are lower than a configured threshold. If this happens over a configured number of runs, then the device is deemed to be idle. In addition my router also shows if the wifi power saving mode is active for a device. So if the data rate is low for some predefined number of runs and its power saving mode is active then it should be turned off. For this I created a free IFTTT account, and then created a webhook which calls a IFTTT maker applet connected to Samsung SmartThings IoT platform. This enabled me to switch off the TV. I put this application as a service on my Raspberry Pi. 
1. First create an IFTTT account.
2. Create a webhook applet on https://ifttt.com/maker_webhooks. For free accounts you can only have 3 applets. For the specified applet event name you would receive a key.
3. Connect your webhook applet to an action like switching off your TV. For this I used Samsung SmartThings.
4. Get the webhook address and append your key to get the url you would hit for switching off your TV.
5. Put the key into the application resources folder with the file name tv_off_key.txt.
6. Build the project using maven.
7. Run the project's jar file.

This project can be extended to make all kinds of automations possible with IoT devices like smart plugs. It has an inbuilt scraper that can be used to trigger events from web activity eg price of an Amazon item hitting a threshold, or monitoring.

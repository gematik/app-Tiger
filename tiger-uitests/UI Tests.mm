<map version="freeplane 1.11.1">
<!--To view this file, download free mind mapping software Freeplane from https://www.freeplane.org -->
<node TEXT="UI Tests" LOCALIZED_STYLE_REF="AutomaticLayout.level.root" FOLDED="false" ID="ID_1090958577" CREATED="1409300609620" MODIFIED="1686650070213">
<hook NAME="accessories/plugins/AutomaticLayout.properties" VALUE="ALL"/>
<font BOLD="true"/>
<hook NAME="MapStyle" background="#f9f9f8" zoom="0.747">
    <properties show_icon_for_attributes="true" edgeColorConfiguration="#808080ff,#ff0000ff,#0000ffff,#00ff00ff,#ff00ffff,#00ffffff,#7c0000ff,#00007cff,#007c00ff,#7c007cff,#007c7cff,#7c7c00ff" show_note_icons="true" associatedTemplateLocation="template:/light_nord_template.mm" fit_to_viewport="false"/>

<map_styles>
<stylenode LOCALIZED_TEXT="styles.root_node" STYLE="oval" UNIFORM_SHAPE="true" VGAP_QUANTITY="24 pt">
<font SIZE="24"/>
<stylenode LOCALIZED_TEXT="styles.predefined" POSITION="bottom_or_right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="default" ID="ID_506805493" ICON_SIZE="12 pt" FORMAT_AS_HYPERLINK="false" COLOR="#484747" BACKGROUND_COLOR="#efefef" STYLE="bubble" SHAPE_HORIZONTAL_MARGIN="8 pt" SHAPE_VERTICAL_MARGIN="5 pt" BORDER_WIDTH_LIKE_EDGE="false" BORDER_WIDTH="1.9 px" BORDER_COLOR_LIKE_EDGE="false" BORDER_COLOR="#8fbcbb" BORDER_DASH_LIKE_EDGE="true" BORDER_DASH="SOLID">
<arrowlink SHAPE="CUBIC_CURVE" COLOR="#000000" WIDTH="2" TRANSPARENCY="200" DASH="" FONT_SIZE="9" FONT_FAMILY="SansSerif" DESTINATION="ID_506805493" STARTARROW="NONE" ENDARROW="DEFAULT"/>
<font NAME="SansSerif" SIZE="11" BOLD="false" STRIKETHROUGH="false" ITALIC="false"/>
<edge STYLE="bezier" COLOR="#2e3440" WIDTH="3" DASH="SOLID"/>
<richcontent CONTENT-TYPE="plain/auto" TYPE="DETAILS"/>
<richcontent TYPE="NOTE" CONTENT-TYPE="plain/auto"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.details" COLOR="#ffffff" BACKGROUND_COLOR="#2e3440" BORDER_WIDTH_LIKE_EDGE="false" BORDER_WIDTH="1.9 px" BORDER_COLOR_LIKE_EDGE="false" BORDER_COLOR="#f0f0f0" BORDER_DASH_LIKE_EDGE="true">
<font SIZE="11" BOLD="false" ITALIC="false"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.attributes">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.note" COLOR="#000000" BACKGROUND_COLOR="#f6f9a1" TEXT_ALIGN="LEFT">
<icon BUILTIN="clock2"/>
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.floating">
<edge STYLE="hide_edge"/>
<cloud COLOR="#f0f0f0" SHAPE="ROUND_RECT"/>
</stylenode>
<stylenode LOCALIZED_TEXT="defaultstyle.selection" COLOR="#eceff4" BACKGROUND_COLOR="#bf616a" BORDER_COLOR_LIKE_EDGE="false" BORDER_COLOR="#bf616a"/>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.user-defined" POSITION="bottom_or_right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="styles.important" ID="ID_915433779" BORDER_COLOR="#bf616a">
<icon BUILTIN="yes"/>
<arrowlink COLOR="#bf616a" TRANSPARENCY="255" DESTINATION="ID_915433779"/>
<font NAME="Ubuntu" SIZE="14"/>
<edge COLOR="#bf616a"/>
</stylenode>
</stylenode>
<stylenode LOCALIZED_TEXT="styles.AutomaticLayout" POSITION="bottom_or_right" STYLE="bubble">
<stylenode LOCALIZED_TEXT="AutomaticLayout.level.root" COLOR="#ffffff" BACKGROUND_COLOR="#484747" STYLE="bubble" SHAPE_HORIZONTAL_MARGIN="10 pt" SHAPE_VERTICAL_MARGIN="10 pt">
<font SIZE="18"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,1" COLOR="#eceff4" BACKGROUND_COLOR="#d08770" STYLE="bubble" SHAPE_HORIZONTAL_MARGIN="8 pt" SHAPE_VERTICAL_MARGIN="5 pt">
<font SIZE="16"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,2" COLOR="#3b4252" BACKGROUND_COLOR="#ebcb8b">
<font SIZE="14"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,3" COLOR="#2e3440" BACKGROUND_COLOR="#a3be8c">
<font SIZE="12"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,4" COLOR="#2e3440" BACKGROUND_COLOR="#b48ead">
<font SIZE="11"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,5" COLOR="#e5e9f0" BACKGROUND_COLOR="#5e81ac">
<font SIZE="11"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,6" BACKGROUND_COLOR="#81a1c1">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,7" BACKGROUND_COLOR="#88c0d0">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,8" BACKGROUND_COLOR="#8fbcbb">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,9" BACKGROUND_COLOR="#d8dee9">
<font SIZE="10"/>
</stylenode>
<stylenode LOCALIZED_TEXT="AutomaticLayout.level,10" BACKGROUND_COLOR="#e5e9f0">
<font SIZE="9"/>
</stylenode>
</stylenode>
</stylenode>
</map_styles>
</hook>
<node TEXT="dynamische Tests" POSITION="bottom_or_right" ID="ID_715638338" CREATED="1686650076251" MODIFIED="1693376585021">
<node TEXT="Sidebar ist geschlossen" FOLDED="true" ID="ID_369423603" CREATED="1686731041886" MODIFIED="1693397794286">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf Tigericon -&gt; Sidebar klappt auf" ID="ID_1436156308" CREATED="1686730092350" MODIFIED="1693397774999">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_842627950" CREATED="1693397776952" MODIFIED="1693397779532"/>
</node>
<node TEXT="Klick auf Statusicon -&gt; Sidebar klappt auf" ID="ID_1964297201" CREATED="1686744879123" MODIFIED="1693397775001">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_652027887" CREATED="1693397779975" MODIFIED="1693397782090"/>
</node>
<node TEXT="Klick auf Featureicon -&gt; Sidebar klappt auf" ID="ID_1915643194" CREATED="1686730092350" MODIFIED="1693397775001">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_1467806800" CREATED="1693397784503" MODIFIED="1693397786275"/>
</node>
<node TEXT="Klick auf Servericon -&gt; Sidebar klappt auf" ID="ID_366995486" CREATED="1686730092350" MODIFIED="1693397775001">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_1799318822" CREATED="1693397789215" MODIFIED="1693397791258"/>
</node>
</node>
<node TEXT="Sidebar ist geöffnet" FOLDED="true" ID="ID_1772734540" CREATED="1686731061866" MODIFIED="1695035718252">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf Doppelpfeil an Sidebar -&gt; Sidebar wird geschlossen" ID="ID_570389721" CREATED="1686652082605" MODIFIED="1693397676471">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedWhenClickedOnDoubleArrow" ID="ID_783284783" CREATED="1693397676776" MODIFIED="1693397690143"/>
</node>
<node TEXT="Klick auf Statusicon -&gt; Sidebar wird geschlossen" ID="ID_776802681" CREATED="1686652082605" MODIFIED="1693397749454">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_1700584752" CREATED="1693397719616" MODIFIED="1693397737867"/>
</node>
<node TEXT="Klick auf Featureicon -&gt; Sidebar wird geschlossen" ID="ID_371217115" CREATED="1686745232452" MODIFIED="1693397749456">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_554407953" CREATED="1693397739335" MODIFIED="1693397740619"/>
</node>
<node TEXT="Klick auf Servericon -&gt; Sidebar wird geschlossen" ID="ID_928148713" CREATED="1686745278576" MODIFIED="1693397749456">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testSidebarIsClosedAndOpensOnIconClickAndClosesAgain" ID="ID_1901863796" CREATED="1693397743167" MODIFIED="1693397744186"/>
</node>
<node TEXT="Interaction" FOLDED="true" ID="ID_1634883158" CREATED="1686651920454" MODIFIED="1693398016670">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf Pausebutton -&gt; Featuretests pausieren, Sidebar ist gelb, Pausebutton grün mit Pfeil, letzte Step in Featureliste hat ein Spinnericon" ID="ID_93302054" CREATED="1686651921389" MODIFIED="1693398012966">
<icon BUILTIN="button_ok"/>
<node TEXT="TPauseTests.testPauseButton" ID="ID_1750116638" CREATED="1693397820823" MODIFIED="1693397834034"/>
</node>
<node TEXT="Klick auf Quitbutton -&gt; Banner &quot;Quit on user request&quot; erscheint, Sidebar ist rot" ID="ID_1374072177" CREATED="1686657173757" MODIFIED="1693398010550">
<icon BUILTIN="button_ok"/>
<node TEXT="ZQuitTests.testQuitButton" ID="ID_1781118518" CREATED="1693397927334" MODIFIED="1693397940284">
<node TEXT="auf rot wird nicht getestet, aber auf das class Attribute" ID="ID_1769008213" CREATED="1693397953279" MODIFIED="1693398004034"/>
</node>
</node>
</node>
<node TEXT="Featurebox" ID="ID_1533654545" CREATED="1686729634486" MODIFIED="1695035632972">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf das letzte Scenario -&gt; Mainwindow scollt zum entsprechenden Scenario" ID="ID_1071379137" CREATED="1686729654120" MODIFIED="1695035652012">
<icon BUILTIN="button_ok"/>
<node TEXT="XYFeatures.testScrollingToKlickedLastTestfile" ID="ID_1897178172" CREATED="1695035675877" MODIFIED="1695037681408"/>
</node>
<node TEXT="Klick aufs erste Scenario -&gt; Mainwindow scollt wieder ganz nach oben" ID="ID_994733362" CREATED="1686729729857" MODIFIED="1695035636357">
<icon BUILTIN="button_ok"/>
<node TEXT="XYFeatures.testScrollingToKlickedFirstTestfile" ID="ID_1519515128" CREATED="1695035694653" MODIFIED="1695037677126"/>
</node>
<node TEXT="Status der Scenarios entspricht dem jeweiligen Status (Passed, Failer, Executing)" ID="ID_1085221591" CREATED="1687253302888" MODIFIED="1695037703473">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="XDynamicSidebarTests.testFindFailedStepInFeatureBoxAndInExecutionPane" ID="ID_1900908317" CREATED="1693399221083" MODIFIED="1693399239719"/>
<node TEXT="testFeatureBoxClickOnLastSecnario.testFeatureBoxClickOnLastSecnario" ID="ID_404245267" CREATED="1693399256235" MODIFIED="1695037712800"/>
</node>
</node>
<node TEXT="Server" FOLDED="true" ID="ID_525262425" CREATED="1686729780737" MODIFIED="1695024400857">
<icon BUILTIN="button_ok"/>
<node TEXT="local_tiger_proxy" ID="ID_1289753665" CREATED="1686730229025" MODIFIED="1693397632183">
<icon BUILTIN="button_ok"/>
<node TEXT=" Klick auf Shortcuticon -&gt; neuer Tab mit der URL öffnet sich" ID="ID_1697932770" CREATED="1686730173547" MODIFIED="1693397607263">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.ServerBoxTigerProxyWebUiStarted" ID="ID_572957248" CREATED="1693396900386" MODIFIED="1693396917602"/>
</node>
<node TEXT="Klick auf Doppelpfeil -&gt; Logs vom Server öffnet sich" ID="ID_467358040" CREATED="1686730250233" MODIFIED="1693397625975">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.ServerBoxLocalTigerProxyLogfiles" ID="ID_1302264712" CREATED="1693396958412" MODIFIED="1693396971736"/>
</node>
</node>
<node TEXT="httpbin" ID="ID_425963800" CREATED="1686730229025" MODIFIED="1693396852004">
<node TEXT="Klick auf Doppelpfeil -&gt; Logs vom Server öffnet sich -&gt; letzter Eintrag &quot;httpbin READY&quot;" ID="ID_1002145291" CREATED="1686730250233" MODIFIED="1695024397976">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.ServerBoxLocalTigerProxyLogfiles" ID="ID_1501127110" CREATED="1693397000321" MODIFIED="1693397002715"/>
</node>
</node>
<node TEXT="remoteTigerProxy" ID="ID_825477375" CREATED="1686730229025" MODIFIED="1686730609402">
<node TEXT=" Klick auf Shortcuticon -&gt; neuer Tab mit der URL öffnet sich" ID="ID_839568579" CREATED="1686730173547" MODIFIED="1693397594999">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.ServerBoxTigerProxyWebUiStarted" ID="ID_794796892" CREATED="1693396918354" MODIFIED="1693396919494"/>
</node>
<node TEXT="Klick auf Doppelpfeil -&gt; Logs vom Server öffnet sich -&gt; letzter Eintrag &quot;remoteTigerProxy READY&quot;" ID="ID_817165535" CREATED="1686730250233" MODIFIED="1695024394951">
<icon BUILTIN="button_ok"/>
<node ID="ID_26784940" CREATED="1693397003828" MODIFIED="1693397003828"><richcontent TYPE="NODE">

<html>
  <head>
    
  </head>
  <body>
    <p>
      XDynamicSidebarTests.ServerBoxLocalTigerProxyLogfiles
    </p>
  </body>
</html>
</richcontent>
</node>
</node>
</node>
</node>
</node>
<node TEXT="Test Execution Pane ist eingeschaltet" ID="ID_485803254" CREATED="1686651915846" MODIFIED="1693472289138">
<icon BUILTIN="help"/>
<node TEXT="Rbel Log Details Bar geschlossen" FOLDED="true" ID="ID_1604639724" CREATED="1686730787984" MODIFIED="1693462694882">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf Doppelpfeil öffnet Rbel Log Details Bar" FOLDED="true" ID="ID_1522317186" CREATED="1686730746646" MODIFIED="1693401064379">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneOpensAndCloses" ID="ID_694610224" CREATED="1693401022677" MODIFIED="1693401031744"/>
</node>
<node TEXT="Klick auf ein GET Request öffnet Rbel Log Details Bar" ID="ID_560697814" CREATED="1686651917199" MODIFIED="1693462624347">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicMainContentTests.testClickOnRequestOpensRbelLogDetails" ID="ID_43710853" CREATED="1693462608913" MODIFIED="1693462622493"/>
</node>
<node TEXT=" Klick auf Shortcuticon -&gt; neuer Tab mit der URL öffnet sich" ID="ID_718429131" CREATED="1686730173547" MODIFIED="1693462692386">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testExecutionPaneRbelOpenWebUiURLCheckNavBarButtons" ID="ID_1800245444" CREATED="1693462676874" MODIFIED="1693462690118"/>
</node>
</node>
<node TEXT="Rbel Log Details Bar geöffnet" FOLDED="true" ID="ID_1378002908" CREATED="1686730787984" MODIFIED="1695024280089">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf Doppelpfeil schließt Rbel Log Details Bar" ID="ID_1580390079" CREATED="1686730746646" MODIFIED="1693401021228">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneOpensAndCloses" ID="ID_1086434327" CREATED="1693401022677" MODIFIED="1693401031744"/>
</node>
<node TEXT="Klick auf ein GET Request der am unteren Ende des Mainwindow liegt -&gt; Rbel Log Details Ansicht scrollt zum entsprechenden Rbel Log" ID="ID_1526181082" CREATED="1686651917199" MODIFIED="1695024276632">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testClickOnLastRequestChangesPageNumberInRbelLogDetails" ID="ID_1205567861" CREATED="1693472210822" MODIFIED="1695024273611"/>
</node>
<node TEXT="Klick auf Hide Button -&gt; DropUp klappt auf" ID="ID_870393950" CREATED="1686812698725" MODIFIED="1694686342562">
<icon BUILTIN="button_ok"/>
<node TEXT="Hidebutton ist aufgeklappt" ID="ID_1285749602" CREATED="1686812844708" MODIFIED="1694686338770">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf Hide Details -&gt; DropUp schließt, Ansicht im Hintergrund ändert sich, Headers und Bodys sind geschlossen" ID="ID_1292576699" CREATED="1686747743073" MODIFIED="1694686327866">
<icon BUILTIN="button_ok"/>
<node TEXT="Erneutes Öffnen des Hidebuttons zeigt das der Hide Details Icon am Button rot ist" ID="ID_1520237447" CREATED="1686813198787" MODIFIED="1694686305834">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneHideDetailsButton" ID="ID_1769973984" CREATED="1694686313419" MODIFIED="1694686315525"/>
</node>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneHideDetailsButton" ID="ID_893371343" CREATED="1693400069289" MODIFIED="1693400079596"/>
</node>
<node TEXT="Klick auf Hide Headers -&gt; DropUp schließt, Ansicht im Hintergrund ändert sich, Header sind geschlossen" ID="ID_758753182" CREATED="1686747743073" MODIFIED="1694686331178">
<icon BUILTIN="button_ok"/>
<node TEXT="Erneutes Öffnen des Hidebuttons zeigt das der Hide Headers Icon am Button rot ist" ID="ID_795154772" CREATED="1686813198787" MODIFIED="1694686324090">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneHideHeaderButton" ID="ID_1923289692" CREATED="1694686318509" MODIFIED="1694686319974"/>
</node>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneHideHeaderButton" ID="ID_636042496" CREATED="1693400048896" MODIFIED="1693400085929"/>
</node>
</node>
</node>
<node TEXT="Klick auf Filterbutton -&gt; Modal öffnet sich" ID="ID_112524417" CREATED="1686747921132" MODIFIED="1694608520752">
<icon BUILTIN="button_ok"/>
<node TEXT="Filtermodal ist geöffnet" ID="ID_136554326" CREATED="1686747963956" MODIFIED="1694608514999">
<icon BUILTIN="button_ok"/>
<node TEXT="Filter ist leer. &quot;Request from&quot; und &quot;Requst to&quot; zeigen beide &quot;no request&quot;. Anzeige &quot;Filter didn&apos;t match any of the XY messages.&quot; wird angezeigt." ID="ID_24574209" CREATED="1686747976655" MODIFIED="1693400563598">
<icon BUILTIN="button_ok"/>
<node TEXT="XLaterTests.testFilterModal" ID="ID_438436359" CREATED="1693400564718" MODIFIED="1693400581334"/>
</node>
<node TEXT="Einen Filter von &quot;Request from&quot;  auswählen und auf Set Filter Button klicken. -&gt; Anzeige ändert sich auf  &quot;XY of ABC did match the filter criteria.&quot; Und im RbelPath filter steht der entsprechende Filter. Hintergrund Messages ändern sich." ID="ID_188057989" CREATED="1686748139690" MODIFIED="1693400695421">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf &quot;Reset Filter&quot; -&gt; RbelPath filter ist leer, Anzeige ändert sich auf &quot;Filter didn&apos;t match any of the XY messages.&quot;" ID="ID_285926844" CREATED="1686748408021" MODIFIED="1693400689949">
<icon BUILTIN="button_ok"/>
<node TEXT="XLaterTests.testFilterModalResetFilter" ID="ID_99126318" CREATED="1693400564718" MODIFIED="1693400644449"/>
</node>
<node TEXT="XLaterTests.testFilterModalResetFilter" ID="ID_101161958" CREATED="1693400564718" MODIFIED="1693400753776"/>
</node>
<node TEXT="Einen Filter im RbelPath setzen der nichts findet, z.B.: $.body.quatsch und auf Set Filter Button klicken. -&gt; Anzeige ändert sich auf  &quot;0 of  ABC did match the filter criteria.&quot; Im Hintergrund sind keine Request Messages zu sehen." ID="ID_385009266" CREATED="1686748139690" MODIFIED="1693400872957">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf &quot;Reset Filter&quot; -&gt; RbelPath filter ist leer, Anzeige ändert sich auf &quot;Filter didn&apos;t match any of the XY messages.&quot;" ID="ID_715006004" CREATED="1686748408021" MODIFIED="1686748713642"/>
<node TEXT="XLaterTests.testFilterModalSetSenderFilter" ID="ID_1574328760" CREATED="1693400564718" MODIFIED="1693400622794"/>
</node>
</node>
</node>
<node TEXT="Klick auf Savebutton -&gt; Modal öffnet sich" FOLDED="true" ID="ID_654545146" CREATED="1686747921132" MODIFIED="1693400420208">
<icon BUILTIN="button_ok"/>
<node TEXT="Savemodal ist geöffnet" ID="ID_57506497" CREATED="1686747963956" MODIFIED="1693400420208">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf &quot;Download HTML&quot; -&gt; html Datei wird heruntergeladen" ID="ID_263062168" CREATED="1686747976655" MODIFIED="1693400420206">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testSaveModalDownloadHtml" ID="ID_518025947" CREATED="1693400381566" MODIFIED="1693400412572"/>
</node>
<node TEXT="Klick auf &quot;Download Traffic&quot; -&gt; tgr Datei wird heruntergeladen" ID="ID_1674788917" CREATED="1686748139690" MODIFIED="1693400420208">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testSaveModalDownloadTgr" ID="ID_1565782108" CREATED="1693400381566" MODIFIED="1693400393376"/>
</node>
<node TEXT="Modalinhalte vorhanden" ID="ID_84288308" CREATED="1693400463310" MODIFIED="1693400499373">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testSaveModal" ID="ID_921909377" CREATED="1693400480023" MODIFIED="1693400495434"/>
</node>
</node>
</node>
<node TEXT="Klick auf Pagebutton -&gt; DropUp öffnet sich" ID="ID_1102565984" CREATED="1686747921132" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="DropUp ist geöffnet" ID="ID_1913159250" CREATED="1686747963956" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf eine andere Page als im Button angezeigt" ID="ID_553060020" CREATED="1686747976655" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="Button schließt und die Darstellung im Hintergrund ändert sich" ID="ID_301196157" CREATED="1686819003695" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testPageButton" ID="ID_1617577145" CREATED="1693400276351" MODIFIED="1693400292017"/>
</node>
</node>
</node>
</node>
<node TEXT="Klick auf Sizebutton -&gt; DropUp öffnet sich" ID="ID_917108561" CREATED="1686747921132" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="DropUp ist geöffnet" ID="ID_876555816" CREATED="1686747963956" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="Klick auf eine andere Size als im Button angezeigt" ID="ID_855533970" CREATED="1686747976655" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="Button schließt und die Darstellung im Hintergrund ändert sich" ID="ID_1981693862" CREATED="1686819003695" MODIFIED="1693400317888">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testSizeButton" ID="ID_40999364" CREATED="1693400293423" MODIFIED="1693400321271"/>
</node>
</node>
</node>
</node>
<node TEXT="Test der Pagination, sprich wenn nur die ersten 10 dargestellt sind und ich klick auf Request 15, dann muss die neue Page geladen werden und 15 in Sicht gescrollt werden" POSITION="bottom_or_right" ID="ID_414376315" CREATED="1687253396789" MODIFIED="1693472007515">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="XYDynamicRbelLogTests.testClickOnLastRequestChangesPageNumberInRbelLogDetails" ID="ID_337088315" CREATED="1693472210822" MODIFIED="1695024273611"/>
</node>
</node>
<node TEXT="WebUI geöffnet" ID="ID_99483655" CREATED="1693400189350" MODIFIED="1695035740195">
<icon BUILTIN="button_ok"/>
<node TEXT="alle Buttons vorhanden" ID="ID_1134217559" CREATED="1693400198542" MODIFIED="1693400229367">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testExecutionPaneRbelOpenWebUiURLCheckNavBarButtons" ID="ID_219602046" CREATED="1693400209151" MODIFIED="1693400226083"/>
</node>
<node TEXT="reset button löscht einträge" ID="ID_1546648866" CREATED="1693400235455" MODIFIED="1693479135530">
<icon BUILTIN="button_ok"/>
<node TEXT="ZQuitTests.testResetButton" ID="ID_752993239" CREATED="1693479121248" MODIFIED="1693479132662"/>
</node>
<node TEXT="RoutingModal existiert" ID="ID_1188576412" CREATED="1693480390191" MODIFIED="1693480516166">
<icon BUILTIN="button_ok"/>
<node TEXT="XLatertests.testRoutingModal" ID="ID_117909499" CREATED="1693480425551" MODIFIED="1693480512018"/>
</node>
</node>
<node TEXT="Step mit pausierter Testausführung" ID="ID_1932516808" CREATED="1686747125164" MODIFIED="1686747508757">
<node TEXT="Klick auf &quot;Weiter&quot; -&gt; Testausführung läuft weiter" ID="ID_846588800" CREATED="1686747513923" MODIFIED="1686747540684"/>
</node>
<node TEXT="Step mit pass/fail Testausführung" ID="ID_384548713" CREATED="1686747125164" MODIFIED="1687506755852">
<node TEXT="Klick auf &quot;Pass&quot; -&gt; Testausführung läuft weiter" ID="ID_440391971" CREATED="1686747513923" MODIFIED="1687506762139"/>
<node TEXT="Klick auf &quot;Fail&quot; -&gt; Testausführung failed" ID="ID_696967340" CREATED="1687506765897" MODIFIED="1687506791015"/>
</node>
<node TEXT="Überprüfung der Step Darstellungen" ID="ID_954824852" CREATED="1687253589406" MODIFIED="1695023830594">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="Plain Steps" ID="ID_1194186418" CREATED="1687253604213" MODIFIED="1695023818203">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
</node>
<node TEXT="Steps mit Parameters (numbers, strings)" ID="ID_1053445786" CREATED="1687253610074" MODIFIED="1695023820913">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
</node>
<node TEXT="Steps mit Datatables" ID="ID_520808705" CREATED="1687253621090" MODIFIED="1695023825553">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
</node>
<node TEXT="Steps in Scenario Outlines" ID="ID_643608361" CREATED="1687253626541" MODIFIED="1695023828250">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
</node>
</node>
<node TEXT="Überprüfung der Scenario Outline Darstellungen" ID="ID_527270124" CREATED="1687253638539" MODIFIED="1695023847313">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="SInd die Beispiele korrekt angefürht" ID="ID_309252394" CREATED="1687253653286" MODIFIED="1687253742463">
<font BOLD="true"/>
</node>
<node TEXT="Auch dann wenn es mehrere Beispiel sektionen gibt?" ID="ID_1598256387" CREATED="1687253665550" MODIFIED="1687253742451">
<font BOLD="true"/>
</node>
</node>
<node TEXT="Status in den Steps sind korrekt dargestellt" ID="ID_869492233" CREATED="1687253680610" MODIFIED="1693475241061">
<icon BUILTIN="button_cancel"/>
<font BOLD="true"/>
<node TEXT="Passed" ID="ID_1130666288" CREATED="1687253759060" MODIFIED="1693475225309">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="XDynamicSidebarTests.testPassedStepInFeatureBoxAndInExecutionPane" ID="ID_15898680" CREATED="1693472356196" MODIFIED="1693472383655"/>
<node TEXT="StaticMainContentTests.testPassedScenario" ID="ID_424849411" CREATED="1693472366485" MODIFIED="1693475189045"/>
</node>
<node TEXT="Skipped" ID="ID_1992501917" CREATED="1687253767583" MODIFIED="1695024109041">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="XDynamicSidebarTests.testExecutionPaneScenariosExists" ID="ID_1064834175" CREATED="1695024091320" MODIFIED="1695024106351"/>
</node>
<node TEXT="Failed" ID="ID_1722229633" CREATED="1693472398083" MODIFIED="1693475225308">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testFindFailedStepInFeatureBoxAndInExecutionPane" POSITION="bottom_or_right" ID="ID_279175169" CREATED="1693472366485" MODIFIED="1693472387717"/>
<node TEXT="StaticMainContentTests.testFailedScenario" ID="ID_1362464219" CREATED="1693472366485" MODIFIED="1693475196820"/>
</node>
<node TEXT="Unknown" ID="ID_153799395" CREATED="1687253771013" MODIFIED="1693475238291">
<icon BUILTIN="button_cancel"/>
<font BOLD="true"/>
</node>
<node TEXT="Currently running" ID="ID_1357647997" CREATED="1687253774610" MODIFIED="1693475225309">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="StaticMainContentTests.testExecutingScenario" ID="ID_205028836" CREATED="1693472366485" MODIFIED="1693475177949"/>
</node>
</node>
<node TEXT="Requests sind den STeps korrekt zugeordnet" ID="ID_106020007" CREATED="1687253802375" MODIFIED="1687253816223">
<font BOLD="true"/>
<node TEXT="Auch mehrere Requests sind korrekt zugeordnet" ID="ID_1626099278" CREATED="1687253817028" MODIFIED="1687253826694">
<font BOLD="true"/>
<node TEXT="XYFeatures.testRequestsAreCorrectInScenario" ID="ID_1046697397" CREATED="1695047114724" MODIFIED="1695047124417"/>
</node>
</node>
<node TEXT="Auch mehrere Featurefiles mit jeweils mehreren Scnearios und Scneario outlines werden korrekt dargestellt" FOLDED="true" ID="ID_1929442512" CREATED="1687253841480" MODIFIED="1695035756897">
<icon BUILTIN="button_ok"/>
<font BOLD="true"/>
<node TEXT="Status, Featurebox haben korrekte STatus icons / Zahlen" ID="ID_1165810301" CREATED="1687253909519" MODIFIED="1695035756895">
<font BOLD="true"/>
<node TEXT="XYFeatures" ID="ID_368894310" CREATED="1695024195378" MODIFIED="1695024196610"/>
</node>
<node TEXT="Deutsche und Englische Featurefiles werden korrekt unterstützt" ID="ID_488867029" CREATED="1687253938836" MODIFIED="1687253963804">
<font BOLD="true"/>
<node TEXT="Zwei Featurefiles vorhanden, eins englisch, eins deutsch" ID="ID_1993075712" CREATED="1695024156900" MODIFIED="1695024178520"/>
</node>
</node>
</node>
<node TEXT="Server Logs Pane ist eingeschaltet" FOLDED="true" ID="ID_36744429" CREATED="1686737011798" MODIFIED="1695023807746">
<icon BUILTIN="button_ok"/>
<node TEXT="localTigerProxy Button ist gedrückt" ID="ID_715852550" CREATED="1686737070134" MODIFIED="1686737099683">
<node TEXT="alle Logs haben in der ersten Spalte &quot;localTigerProxy&quot; stehen" ID="ID_876596301" CREATED="1686737104716" MODIFIED="1686737146453">
<node TEXT="XDynamicMainContentTests.testServerLogsOfServerShown" ID="ID_1154942068" CREATED="1693482919554" MODIFIED="1693482931171"/>
</node>
</node>
<node TEXT="remoteTigerButton ist gedrückt" ID="ID_1544271157" CREATED="1686737070134" MODIFIED="1686737176003">
<node TEXT="alle Logs haben in der ersten Spalte &quot;remoteTigerProxy&quot; stehen" ID="ID_654054839" CREATED="1686737104716" MODIFIED="1686737193778">
<node TEXT="XDynamicMainContentTests.testServerLogLogsOfServerShown" ID="ID_835929419" CREATED="1693482919554" MODIFIED="1693485696388"/>
</node>
</node>
<node TEXT="httpbin Button ist gedrückt" ID="ID_218226034" CREATED="1686737070134" MODIFIED="1693482942793">
<node TEXT="alle Logs haben in der ersten Spalte &quot;httpbin&quot; stehen" ID="ID_858364170" CREATED="1686737104716" MODIFIED="1693482952578">
<node TEXT="XDynamicMainContentTests.testServerLogsOfServerShown" ID="ID_1199066166" CREATED="1693482919554" MODIFIED="1693482931171"/>
</node>
</node>
<node TEXT="localTigerProxy und remoteTigerProxy ist gedrückt" ID="ID_325944604" CREATED="1686737218004" MODIFIED="1686737239917">
<node TEXT="alle Logs haben in der ersten Spalte KEIN &quot;winstone&quot; stehen" ID="ID_1281681483" CREATED="1686737104716" MODIFIED="1686737252411">
<node TEXT="XDynamicMainContentTests.testServerNoLogsOfHttpbinShown" ID="ID_740475059" CREATED="1693482919554" MODIFIED="1693483065966"/>
</node>
</node>
<node TEXT="Im Textfeld gibt man &quot;ready&quot; ein" ID="ID_1644788931" CREATED="1686737262037" MODIFIED="1686737318203">
<node TEXT="Kein Log ist dargestellt" ID="ID_729363326" CREATED="1686737320672" MODIFIED="1686737334958">
<node TEXT="XDynamicMainContentTests.testServerLogNoLogsShown" ID="ID_1326537124" CREATED="1693483512607" MODIFIED="1693483522380"/>
</node>
</node>
<node TEXT="Im Textfeld gibt man &quot;READY&quot; ein" ID="ID_1160256899" CREATED="1686737262037" MODIFIED="1686737346672">
<node TEXT="2 Logs werden angezeigt" ID="ID_1695425763" CREATED="1686737320672" MODIFIED="1686737368273">
<node TEXT="XDynamicMainContentTests.testServerLogTwoLogsShown" ID="ID_1225292346" CREATED="1693483512607" MODIFIED="1693483579465"/>
</node>
</node>
<node TEXT="Im Loglevel wird ERROR ausgewählt" ID="ID_1495729416" CREATED="1686737262037" MODIFIED="1686737427178">
<node TEXT="Kein Log ist dargestellt" ID="ID_373794879" CREATED="1686737320672" MODIFIED="1686737334958">
<node TEXT="XDynamicMainContentTests.testServerLogNoLogsShownOnErrorLevel" ID="ID_284058821" CREATED="1693483512607" MODIFIED="1695023449681"/>
</node>
</node>
<node TEXT="Im Loglevel wird INFO ausgewählt und im Textfeld gibt man &quot;started&quot; ein" ID="ID_180191861" CREATED="1686737262037" MODIFIED="1686737502299">
<node TEXT="5 Logs werden dargestellt" ID="ID_1647453498" CREATED="1686737320672" MODIFIED="1686737518992">
<node TEXT="XDynamicMainContentTests.testServerLogLogsShownOnInfoLevel" ID="ID_1008608758" CREATED="1693483512607" MODIFIED="1695023801140"/>
</node>
</node>
</node>
</node>
<node TEXT="statische Tests" POSITION="bottom_or_right" ID="ID_695180498" CREATED="1686655070308" MODIFIED="1686736054026">
<node TEXT="Sidebartest" FOLDED="true" ID="ID_1476200117" CREATED="1686652069678" MODIFIED="1694685923892">
<icon BUILTIN="button_ok"/>
<node TEXT="Sidebar ist geschlossen beim Startup" FOLDED="true" ID="ID_1564415889" CREATED="1686651897190" MODIFIED="1693399104076" TEXT_SHORTENED="true">
<icon BUILTIN="button_ok"/>
<node TEXT="Tigericon ist sichtbar" ID="ID_83445342" CREATED="1686653524002" MODIFIED="1693399104074">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarClosedIconsAreVisible" ID="ID_1223151386" CREATED="1693394615298" MODIFIED="1693394650707"/>
</node>
<node TEXT="Quitbutton ist sichtbar" ID="ID_180226285" CREATED="1686653547953" MODIFIED="1693399104076">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarClosedIconsAreVisible" ID="ID_74821588" CREATED="1693394640299" MODIFIED="1693394654670"/>
</node>
<node TEXT="Pausebutton ist sichtbar" ID="ID_1904002314" CREATED="1686653547953" MODIFIED="1693399104076">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarClosedIconsAreVisible" ID="ID_854610801" CREATED="1693394655082" MODIFIED="1693394657878"/>
</node>
<node TEXT="Statusbutton ist sichtbar" ID="ID_890149898" CREATED="1686653558329" MODIFIED="1693399104076">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarClosedIconsAreVisible" ID="ID_818063496" CREATED="1693394659474" MODIFIED="1693394660967"/>
</node>
<node TEXT="Featurebutton ist sichtbar" ID="ID_746367983" CREATED="1686653558801" MODIFIED="1693399104076">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarClosedIconsAreVisible" ID="ID_767156616" CREATED="1693394661793" MODIFIED="1693394663173"/>
</node>
<node TEXT="Serverbutton ist sichtbar" ID="ID_1807058944" CREATED="1686653559145" MODIFIED="1693399104076">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarClosedIconsAreVisible" ID="ID_1189403184" CREATED="1693394679082" MODIFIED="1693394680485"/>
</node>
</node>
<node TEXT="Sidebar ist aufgeklappt" ID="ID_161096072" CREATED="1686653552457" MODIFIED="1694685917315">
<icon BUILTIN="button_ok"/>
<node TEXT="Icon ist sichtbar und Titel &quot;Workflow UI&quot; ist sichtbar" ID="ID_1389127772" CREATED="1686654058775" MODIFIED="1693399093946">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_1652623730" CREATED="1693394694586" MODIFIED="1693395696669"/>
</node>
<node TEXT="Statusbox" ID="ID_1030420388" CREATED="1686654060311" MODIFIED="1694608033507">
<icon BUILTIN="button_ok"/>
<node TEXT="Statusbutton sichtbar" ID="ID_948933394" CREATED="1686654802653" MODIFIED="1693396167988">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_1567790552" CREATED="1693395720511" MODIFIED="1693395722201"/>
</node>
<node TEXT="Statusbutton rot" ID="ID_546510906" CREATED="1686654819725" MODIFIED="1694608030153">
<icon BUILTIN="button_ok"/>
<node TEXT="muss wirklich die Farbe geprüft werden?" ID="ID_96755684" CREATED="1693396150645" MODIFIED="1693396162847"/>
</node>
<node TEXT="Überschrift &quot;Status&quot; vorhanden" ID="ID_464059670" CREATED="1686654833845" MODIFIED="1693396172156">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_158012508" CREATED="1693395740974" MODIFIED="1693395742560"/>
</node>
<node TEXT="&quot;Features: x OK y FAIL&quot;" ID="ID_279928265" CREATED="1686654834037" MODIFIED="1693396172157">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testStatus" ID="ID_1159289678" CREATED="1693395806462" MODIFIED="1693395839730"/>
</node>
<node TEXT="&quot;Scenarios: x OK y FAIL&quot;" ID="ID_65286037" CREATED="1686654834277" MODIFIED="1693396172158">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testStatus" ID="ID_1185316040" CREATED="1693395810302" MODIFIED="1693395844860"/>
</node>
<node TEXT="Akutelle Datum und Uhrzeit wird angezeigt" ID="ID_216972977" CREATED="1686654834692" MODIFIED="1693396172158">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testStatus" ID="ID_884004734" CREATED="1693395848189" MODIFIED="1693395849808"/>
</node>
</node>
<node TEXT="Featurebox" ID="ID_689183482" CREATED="1686654060519" MODIFIED="1694677681591">
<icon BUILTIN="button_ok"/>
<node TEXT="Featurebutton sichtbar" ID="ID_416511514" CREATED="1686654957261" MODIFIED="1693396190732">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_1446014187" CREATED="1693395762366" MODIFIED="1693395764104"/>
</node>
<node TEXT="Überschrift &quot;Feature&quot; vorhanden" ID="ID_927478026" CREATED="1686654957844" MODIFIED="1693396190733">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_1177256981" CREATED="1693395767614" MODIFIED="1693395768880"/>
</node>
<node TEXT="xyz Feature vorhanden" ID="ID_626585890" CREATED="1686654958013" MODIFIED="1694677677343">
<icon BUILTIN="button_ok"/>
<node TEXT="XYFeatureTests" ID="ID_650112515" CREATED="1693395935494" MODIFIED="1694685906463">
<node TEXT="hier teste ich nur ob die Anzahl von Feature, Scenario und Outline stimmt" ID="ID_809083185" CREATED="1693396204957" MODIFIED="1693396283535"/>
</node>
</node>
<node TEXT="Get Simple Request vorhanden,  Haken Icon grün vorhanden" ID="ID_1453051956" CREATED="1686654958181" MODIFIED="1694607860386">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testExecutionPaneScenariosExists" ID="ID_1060143099" CREATED="1693398167512" MODIFIED="1693398290224">
<node TEXT="teste hier nur ob es passed tests gibt" ID="ID_467104792" CREATED="1693398308581" MODIFIED="1693398322527"/>
</node>
</node>
<node TEXT="XZY Step vorhanden, Ausrufeicon rot vorhanden" ID="ID_33988575" CREATED="1686654958349" MODIFIED="1694607845467">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testExecutionPaneScenariosExists" ID="ID_572592035" CREATED="1693398292073" MODIFIED="1694607840682">
<node TEXT="teste hier nur ob es failed tests gibt" ID="ID_995587842" CREATED="1693398308581" MODIFIED="1693398369837"/>
</node>
</node>
</node>
<node TEXT="Serverbox" ID="ID_733180077" CREATED="1686654952837" MODIFIED="1693398444557">
<icon BUILTIN="button_ok"/>
<node TEXT="httpbin mit icon und &quot;(RUNNING)&quot; vorhanden" ID="ID_1968754137" CREATED="1686659578199" MODIFIED="1693398393516">
<icon BUILTIN="button_ok"/>
<node TEXT="Adresse vorhanden, Shortcuticon vorhanden" ID="ID_1024750857" CREATED="1686658305995" MODIFIED="1686659654729">
<node TEXT="XDynamicSidebarTests.ServerBoxAllServerRunning" ID="ID_336794032" CREATED="1693396337812" MODIFIED="1693396774763"/>
</node>
</node>
<node TEXT="local_tiger_proxy mit grünem Icon und &quot;(RUNNING)&quot; vorhanden" ID="ID_318887805" CREATED="1686658196404" MODIFIED="1693398424685">
<icon BUILTIN="button_ok"/>
<node TEXT="Adresse vorhanden, Shortcuticon vorhanden" ID="ID_1273527186" CREATED="1686658305995" MODIFIED="1686659645168">
<node TEXT="XDynamicSidebarTests.ServerBoxAllServerRunning" ID="ID_731642669" CREATED="1693396337812" MODIFIED="1693396774763"/>
</node>
</node>
<node TEXT="remoteTigerProxy mit Icon und &quot;(RUNNING)&quot; vorhanden" ID="ID_1079071916" CREATED="1686659677759" MODIFIED="1693398424686">
<icon BUILTIN="button_ok"/>
<node TEXT="Adresse vorhanden, Shortcuticon vorhanden" ID="ID_1722841895" CREATED="1686658305995" MODIFIED="1686659645168">
<node TEXT="XDynamicSidebarTests.ServerBoxAllServerRunning" ID="ID_544218869" CREATED="1693396337812" MODIFIED="1693396774763"/>
</node>
</node>
<node TEXT="Serverbutton sichtbar" ID="ID_772351172" CREATED="1686729905418" MODIFIED="1693398424687">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_713051828" CREATED="1693395884189" MODIFIED="1693395885375"/>
</node>
<node TEXT="Überschrift &quot;Servers&quot; vorhanden" ID="ID_1156529322" CREATED="1686654833845" MODIFIED="1693398424687">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_158878913" CREATED="1693395888365" MODIFIED="1693395889303"/>
</node>
</node>
<node TEXT="Tigerversion vorhanden" ID="ID_1849277977" CREATED="1686654060735" MODIFIED="1693396076997">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_1338686259" CREATED="1693396059165" MODIFIED="1693396062560"/>
</node>
<node TEXT="Buildverion vorhanden" ID="ID_812452153" CREATED="1686654060975" MODIFIED="1693396079749">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticSidebarTests.testSidebarOpenIconsAreVisible" ID="ID_638388363" CREATED="1693396068012" MODIFIED="1693396071566"/>
</node>
</node>
</node>
<node TEXT="MainWindowTest" ID="ID_1430868587" CREATED="1686651923845" MODIFIED="1695036722593">
<icon BUILTIN="button_ok"/>
<node TEXT="ServerLog Tab ist aktiv" ID="ID_1013264471" CREATED="1686653385010" MODIFIED="1695036718753">
<icon BUILTIN="button_ok"/>
<node TEXT="Button &quot;Show all logs&quot; ist aktiviert" ID="ID_1740470425" CREATED="1686735591174" MODIFIED="1693474812780">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testServerLogPaneExists" ID="ID_1411211528" CREATED="1693474762415" MODIFIED="1693474800455"/>
</node>
<node TEXT="Button localTigerProxy, remoteTigerProxy und httpbin sind vorhanden" ID="ID_147235722" CREATED="1686735740072" MODIFIED="1693398946034">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testServerLogPaneButtonsExists" ID="ID_1633323151" CREATED="1693398923820" MODIFIED="1693398934278"/>
</node>
<node TEXT="Textfeld ist leer bzw enthält Kommentar &quot;add text&quot;" ID="ID_1961276276" CREATED="1686735779140" MODIFIED="1693474845685">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testServerPanInputTextIsEmpty" ID="ID_222508254" CREATED="1693474825215" MODIFIED="1693474840416"/>
</node>
<node TEXT="Loglevel ist auf &quot;ALL&quot; gestellt" ID="ID_1349453738" CREATED="1686735815469" MODIFIED="1695036716033">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testServerLogPaneButtonsExists" ID="ID_23383016" CREATED="1695036712211" MODIFIED="1695036713793"/>
</node>
<node TEXT="Das Log enthält Einträge" ID="ID_89570611" CREATED="1686735834078" MODIFIED="1693474923405">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testServerLogPaneButtonsExists" ID="ID_579540169" CREATED="1693398923820" MODIFIED="1693398934278"/>
</node>
</node>
<node TEXT="Test execution is aktiv" FOLDED="true" ID="ID_353592645" CREATED="1686735906257" MODIFIED="1693476460370">
<icon BUILTIN="button_ok"/>
<node TEXT="Es sind Scenarios vorhanden" ID="ID_1382588086" CREATED="1686735922061" MODIFIED="1693476457753">
<icon BUILTIN="button_ok"/>
<node TEXT="XDynamicSidebarTests.testExecutionPaneScenariosExists" ID="ID_1225352522" CREATED="1693476441787" MODIFIED="1693476454676"/>
</node>
<node TEXT="Oben rechts ist das aktuelle Datum mit Uhrzeit zu finden" ID="ID_1734348058" CREATED="1686735959988" MODIFIED="1693475121420">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testExecutionPaneDateTime" ID="ID_1663446173" CREATED="1693475098895" MODIFIED="1693475118079"/>
</node>
<node TEXT="Oben rechts ist das Gematiklogo zu finden" ID="ID_1905945854" CREATED="1686735884649" MODIFIED="1693476263113">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testExecutionPaneGematikLogo" ID="ID_539338149" CREATED="1693475098895" MODIFIED="1693476258492"/>
</node>
<node TEXT="Execution ist aktiv, ServerLogPane inaktiv" ID="ID_850693959" CREATED="1693399020212" MODIFIED="1693399056210">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticMainContentTests.testExecutionPaneActive" ID="ID_645181604" CREATED="1693399041203" MODIFIED="1693399052638"/>
</node>
</node>
</node>
<node TEXT="Rbel Log Details Test" ID="ID_760963648" CREATED="1686736052659" MODIFIED="1693476643995">
<node TEXT="Rbel Log Details Bar ist geöffnet" ID="ID_100256035" CREATED="1686736091530" MODIFIED="1693476643995" HGAP_QUANTITY="14.3 pt">
<icon BUILTIN="help"/>
<node TEXT="Ein Logo und die Überschrift &quot;Rbel Log Details&quot; und das Shortcuticon sind vorhanden" ID="ID_1843431194" CREATED="1686736184103" MODIFIED="1693398698644">
<icon BUILTIN="button_ok"/>
<node TEXT="StaticRbelLogTests.testExecutionPaneRbelLogo" ID="ID_1490339493" CREATED="1693398540117" MODIFIED="1693398548378"/>
<node TEXT="XYDynamicRbelLogTests.testExecutionPaneRbelWebUiURLExists" ID="ID_292914745" CREATED="1693398576637" MODIFIED="1693398587280"/>
</node>
<node TEXT="Es sind 5 Buttons in der NavBar" ID="ID_1475365942" CREATED="1686736112838" MODIFIED="1693476525648">
<icon BUILTIN="button_ok"/>
<node TEXT="XLaterTests.testNavbarWithButtonsExists" ID="ID_1631838312" CREATED="1693476515697" MODIFIED="1693476522948"/>
</node>
<node TEXT="Es sind Requests and Response Nachrichten vorhanden" ID="ID_528012040" CREATED="1686736146881" MODIFIED="1693476617273">
<icon BUILTIN="help"/>
<node TEXT="XLaterTests.testRbelMessagesExists" ID="ID_447143613" CREATED="1693476589059" MODIFIED="1693476612875">
<node TEXT="wir unterscheiden nicht ob request oder reponse" ID="ID_483594718" CREATED="1693476620483" MODIFIED="1693476637427"/>
</node>
</node>
<node TEXT="Öffnen und schließen der RbelLogDetails funktionert" ID="ID_574305805" CREATED="1693398652300" MODIFIED="1693398695828">
<icon BUILTIN="button_ok"/>
<node TEXT="XYDynamicRbelLogTests.testRbelLogPaneOpensAndCloses" ID="ID_496121105" CREATED="1693398677004" MODIFIED="1693398692215"/>
</node>
</node>
</node>
</node>
</node>
</map>

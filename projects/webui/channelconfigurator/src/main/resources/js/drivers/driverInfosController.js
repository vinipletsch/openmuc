(function () {

    var injectParams = ['$scope', '$state', '$alert', '$stateParams', '$translate', 'DriversService', 'DriverDataService'];

    var DriverInfosController = function ($scope, $state, $alert, $stateParams, $translate, DriversService) {


        var deviceWarningrText;
        $translate('DRIVER_INFO_FAILED').then(text = > deviceWarningrText = text
    )
        ;

        $scope.driver = DriversService.getDriver($stateParams.id);
        $scope.driver.infos = {};

        $scope.showInfos = function () {
            $scope.infosDriverForm.submitted = true;

            DriversService.getInfos($scope.driver.id).then(r = > $scope.driver.infos = response,
                e =
        >
            {
                $alert({content: deviceWarningrText, type: 'warning'});
                return $state.go('channelconfigurator.drivers.index');
            }
        )
            ;
        };

    };

    DriverInfosController.$inject = injectParams;

    angular.module('openmuc.drivers').controller('DriverInfosController', DriverInfosController);

})();